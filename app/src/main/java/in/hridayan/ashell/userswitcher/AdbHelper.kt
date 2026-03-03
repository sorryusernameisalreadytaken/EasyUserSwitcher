package `in`.hridayan.ashell.userswitcher

import android.content.Context
import android.util.Log
import `in`.hridayan.ashell.shell.common.data.adb.AdbConnectionManager
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Small helper around [AbsAdbConnectionManager] providing easy methods to pair,
 * connect and execute shell commands. All network operations should be
 * dispatched off the main thread – public suspending functions take care of
 * switching to [Dispatchers.IO].
 */
class AdbHelper(private val context: Context) {

    private val adbManager: AbsAdbConnectionManager by lazy {
        // Use the same global ADB connection manager as the rest of the app. This
        // allows the easy user switcher to piggy‑back on existing connections
        // established via Local ADB, wireless debugging or OTG. Without this,
        // the helper would create a separate connection manager and ignore the
        // connection that may already be open.
        AdbConnectionManager.getInstance(context)
    }

    /**
     * Initiate pairing with the ADB daemon. The host and port refer to the
     * pairing service (not the port used to run commands). On most devices
     * this can be retrieved from the "Wireless debugging" dialog when pairing
     * with a code. Returns true if the pairing succeeds, false otherwise.
     */
    suspend fun pair(host: String, port: Int, pairingCode: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Set the host address to the provided host. This is used by
                // connect() later if no host is explicitly specified.
                adbManager.setHostAddress(host)
                adbManager.pair(host, port, pairingCode)
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed", e)
                false
            }
        }

    /**
     * Connect to an already paired ADB daemon. The daemon is normally
     * listening on port 5555 but may be different depending on the device.
     * Returns true if a new connection was made, false if it was already
     * connected or if an error occurred.
     */
    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            adbManager.setHostAddress(host)
            // If we're already connected this returns false.
            adbManager.connect(port)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            false
        }
    }

    /**
     * Whether a connection is currently open. Note that the connection can
     * drop silently when the device goes to sleep; callers should reconnect
     * as needed.
     */
    fun isConnected(): Boolean {
        return try {
            adbManager.isConnected
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a shell command and returns the output as a string. To
     * determine when the command has finished this helper appends a unique
     * marker to the command and reads until it appears in the output. Returns
     * null on error.
     */
    suspend fun executeShell(command: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext null
        val marker = "__EUS_END__"
        val fullCommand = "$command; echo $marker"
        var stream: io.github.muntashirakon.adb.AdbStream? = null
        try {
            stream = adbManager.openStream("shell:$fullCommand")
            val input = BufferedReader(InputStreamReader(stream.openInputStream()))
            val output = StringBuilder()
            var line: String?
            while (input.readLine().also { line = it } != null) {
                val l = line ?: break
                if (l.contains(marker)) {
                    break
                }
                output.appendLine(l)
            }
            try { input.close() } catch (_: Exception) {}
            try { stream.close() } catch (_: Exception) {}
            output.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command: $command", e)
            try { stream?.close() } catch (_: Exception) {}
            null
        }
    }

    /**
     * Parse the output of `pm list users` and return a list of pairs of
     * (userId, userLabel). Lines are expected to contain `UserInfo{<id>:<name>:`.
     */
    fun parseUsers(output: String): List<Pair<Int, String>> {
        return output.lines()
            .mapNotNull { line ->
                val start = line.indexOf("UserInfo{")
                if (start >= 0) {
                    val end = line.indexOf('}', start)
                    if (end > start) {
                        val inner = line.substring(start + "UserInfo{".length, end)
                        val parts = inner.split(":")
                        if (parts.size >= 2) {
                            val idStr = parts[0]
                            val nameStr = parts[1]
                            idStr.toIntOrNull()?.let { id -> id to nameStr }
                        } else null
                    } else null
                } else null
            }
    }

    /**
     * Switch to a target user and automatically return to the main user once
     * the device becomes idle. The command runs entirely on the device and
     * exits when the switch back is complete. The main user defaults to 0.
     */
    suspend fun switchUser(targetUser: Int, mainUser: Int = 0) = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext
        // Build a shell script that switches to the target user, waits until the
        // device becomes idle (screen off) and then switches back to the main user.
        // The CURRENT user ID is extracted using tr to remove non‑digit characters
        // because `am get-current-user` may include descriptive text on some OSes.
        // We escape the dollar sign to prevent Kotlin from interpreting it as
        // string interpolation. The script is flattened into a single line with
        // semicolons to avoid issues with multi‑line shell input.
        val script = """
            TARGET_USER=$targetUser
            MAIN_USER=$mainUser
            # switch to the target user
            am switch-user ${'$'}TARGET_USER
            sleep 2
            while true; do
                # extract the numeric current user ID
                CURRENT=$(am get-current-user | tr -dc '0-9')
                # exit early if the user has been manually switched away
                if [ "${'$'}CURRENT" != "${'$'}TARGET_USER" ]; then exit 0; fi
                # If the display is off, break out to return to the main user.  The
                # Display Power state is more reliable than the Input Manager state on
                # GrapheneOS.  When the screen is off, dumpsys power will report
                # `Display Power: state=OFF`.
                if dumpsys power | grep -q 'state=OFF'; then break; fi
                sleep 5
            done
            # switch back to the main user
            am switch-user ${'$'}MAIN_USER
        """.trimIndent().replace("\n", "; ")
        // We don't need the output of this command. Fire and forget.
        executeShell(script)
    }

    companion object {
        private const val TAG = "AdbHelper"
    }
}