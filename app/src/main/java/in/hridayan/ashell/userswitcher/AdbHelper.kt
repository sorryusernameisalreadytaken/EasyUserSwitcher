package `in`.hridayan.ashell.userswitcher

import android.content.Context
import android.util.Log
// Use the shared ADB connection manager provided by the rest of the app.
// The EasyAdbConnectionManager was a private implementation specific to the
// user‑switcher feature. To ensure the user switcher reuses the same
// underlying connection as the rest of the app (local ADB, wireless and OTG
// connections), we import the global manager from the shell.common package.
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

    // Lazily obtain the global ADB connection manager.  Using the
    // application context ensures we don't accidentally leak an Activity.  The
    // manager is shared across all components (local ADB, Wi‑Fi, OTG etc.) so
    // that a single connection is reused instead of each feature opening its
    // own connection.  See AdbConnectionManager in the shell.common module.
    private val adbManager: AbsAdbConnectionManager by lazy {
        AdbConnectionManager.getInstance(context.applicationContext)
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
    /**
     * Parse the output of `pm list users` and return a list of triples of
     * (userId, userLabel, isRunning). Lines are expected to contain
     * `UserInfo{<id>:<name>:` followed optionally by `running` at the end.
     */
    fun parseUsers(output: String): List<Triple<Int, String, Boolean>> {
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
                            val running = line.contains("running")
                            idStr.toIntOrNull()?.let { id -> Triple(id, nameStr, running) }
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
    suspend fun switchUser(targetUser: Int, mainUser: Int = 0): String? = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext null
        // Build a simple command to switch to the target user. We'll capture
        // any output for error reporting. If this succeeds, we optionally
        // schedule a return to the main user based on idle detection.
        val switchCommand = "am switch-user $targetUser"
        val result = executeShell(switchCommand)
        // If the command returned anything it may indicate an error. The
        // expected successful invocation typically returns no output.
        if (result?.isNotBlank() == true) {
            return@withContext result
        }
        // Schedule the auto-return to the main user using a background script.
        // We don't need to wait for this to finish, but we still append our
        // marker to ensure the shell command terminates. The script loops
        // until the display turns off and then performs the switch back.
        val script = """
            TARGET_USER=$targetUser
            MAIN_USER=$mainUser
            # Wait until the current user matches the target user to ensure the
            # initial switch is complete. Bail out if switching fails.
            for i in 1 2 3 4 5; do
                CURRENT=$(am get-current-user | tr -dc '0-9')
                [ "${'$'}CURRENT" = "${'$'}TARGET_USER" ] && break
                sleep 1
            done
            # Poll until the display turns off, then switch back to main user
            while true; do
                CURRENT=$(am get-current-user | tr -dc '0-9')
                [ "${'$'}CURRENT" != "${'$'}TARGET_USER" ] && exit 0
                if dumpsys power | grep -q 'state=OFF'; then break; fi
                sleep 5
            done
            am switch-user ${'$'}MAIN_USER
        """.trimIndent().replace("\n", "; ")
        // Fire and forget the background script. We ignore its output.
        executeShell(script)
        return@withContext null
    }

    companion object {
        private const val TAG = "AdbHelper"
    }
}