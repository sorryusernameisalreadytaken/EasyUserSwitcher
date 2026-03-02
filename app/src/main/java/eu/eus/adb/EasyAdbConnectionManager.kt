package eu.eus.adb

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.spec.EncodedKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * A thin wrapper around [AbsAdbConnectionManager] that generates and persists
 * a private key and certificate on first use. The key is used to authenticate
 * this application to the ADB daemon when connecting over a TCP socket. The
 * generated device name is "EasyUserSwitcher" so paired devices will see a
 * descriptive name during pairing.
 */
class EasyAdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private var mPrivateKey: PrivateKey?
    private var mCertificate: Certificate?
    private val mDeviceName = "EasyUserSwitcher"

    init {
        // Always use the current OS API level for the ADB connection. This is
        // necessary for proper feature negotiation with modern Android releases.
        api = Build.VERSION.SDK_INT
        mPrivateKey = readPrivateKeyFromFile(context)
        mCertificate = readCertificateFromFile(context)
        if (mPrivateKey == null || mCertificate == null) {
            // Generate a new RSA key pair. The Android ADB protocol supports
            // 2048‑bit RSA keys and will reject anything shorter. A secure
            // random PRNG is required.
            val keySize = 2048
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(keySize, SecureRandom.getInstance("SHA1PRNG"))
            val generateKeyPair = keyPairGenerator.generateKeyPair()
            val publicKey = generateKeyPair.public
            mPrivateKey = generateKeyPair.private

            // Build a self‑signed X.509 certificate. Android's ADB daemon
            // authenticates clients by verifying the signature against this
            // certificate. The certificate is valid for 24 hours (hard‑coded
            // expiry) which matches ADB's default behaviour.
            // Use the locally stored device name. Interpolating through mDeviceName avoids
            // inadvertently referencing a property that doesn't yet exist in this scope.
            val subject = "CN=${mDeviceName}"
            val algorithmName = "SHA512withRSA"
            val expiryDate = System.currentTimeMillis() + 86400000L
            val certificateExtensions = CertificateExtensions().apply {
                set(
                    "SubjectKeyIdentifier",
                    SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
                )
                val notBefore = Date()
                val notAfter = Date(expiryDate)
                set(
                    "PrivateKeyUsage",
                    PrivateKeyUsageExtension(notBefore, notAfter)
                )
            }
            val x500Name = X500Name(subject)
            val notBefore = Date()
            val notAfter = Date(expiryDate)
            val certificateValidity = CertificateValidity(notBefore, notAfter)
            val x509CertInfo = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set(
                    "serialNumber",
                    CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE)
                )
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithmName)))
                set("subject", CertificateSubjectName(x500Name))
                set("key", CertificateX509Key(publicKey))
                set("validity", certificateValidity)
                set("issuer", CertificateIssuerName(x500Name))
                set("extensions", certificateExtensions)
            }
            val x509CertImpl = X509CertImpl(x509CertInfo).apply {
                sign(mPrivateKey, algorithmName)
            }
            mCertificate = x509CertImpl

            // Persist key and certificate to the app's private directory. These
            // files are world‑readable only by our app and survive across
            // restarts so the user doesn't have to re‑pair after every run.
            writePrivateKeyToFile(context, mPrivateKey!!)
            writeCertificateToFile(context, mCertificate!!)
        }
    }

    override fun getPrivateKey(): PrivateKey = mPrivateKey!!

    override fun getCertificate(): Certificate = mCertificate!!

    override fun getDeviceName(): String = mDeviceName

    companion object {
        @Volatile
        private var INSTANCE: AbsAdbConnectionManager? = null

        /**
         * Obtain a singleton instance of the ADB connection manager. Each call
         * returns the same object to preserve open connections and cached
         * authentication state.
         */
        @Throws(Exception::class)
        fun getInstance(context: Context): AbsAdbConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EasyAdbConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        @Throws(IOException::class, CertificateException::class)
        private fun readCertificateFromFile(context: Context): Certificate? {
            val certFile = File(context.filesDir, "cert.pem")
            if (!certFile.exists()) return null
            FileInputStream(certFile).use { cert ->
                return CertificateFactory.getInstance("X.509").generateCertificate(cert)
            }
        }

        @Throws(CertificateEncodingException::class, IOException::class)
        private fun writeCertificateToFile(context: Context, certificate: Certificate) {
            val certFile = File(context.filesDir, "cert.pem")
            val encoder = BASE64Encoder()
            FileOutputStream(certFile).use { os ->
                os.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
                os.write('\n'.code)
                encoder.encode(certificate.encoded, os)
                os.write('\n'.code)
                os.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
            }
        }

        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
            val privateKeyFile = File(context.filesDir, "private.key")
            if (!privateKeyFile.exists()) return null
            val privKeyBytes = ByteArray(privateKeyFile.length().toInt())
            FileInputStream(privateKeyFile).use { stream ->
                stream.read(privKeyBytes)
            }
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec: EncodedKeySpec = PKCS8EncodedKeySpec(privKeyBytes)
            return keyFactory.generatePrivate(privateKeySpec)
        }

        @Throws(IOException::class)
        private fun writePrivateKeyToFile(context: Context, privateKey: PrivateKey) {
            val privateKeyFile = File(context.filesDir, "private.key")
            FileOutputStream(privateKeyFile).use { os ->
                os.write(privateKey.encoded)
            }
        }
    }
}