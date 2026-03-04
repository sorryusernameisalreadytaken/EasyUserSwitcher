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
     * Switch to a target user and, if requested, schedule an automatic return
     * to the main user once the device display becomes inactive.  The
     * automatic return logic mirrors the manual command shown in the
     * project documentation: it continuously polls `dumpsys input` for the
     * `Interactive = true` flag and, when the flag flips to false (i.e. the
     * screen has turned off), waits briefly before issuing `am switch-user
     * mainUser` to return to the owner.  To avoid blocking the caller on
     * potentially long‑running polling, the return logic is run in a
     * background subshell using the ampersand operator.  This ensures that
     * the adb stream returns immediately while the polling continues on the
     * device.  Any non‑empty output from the initial switch command is
     * returned to the caller to signal an error.
     */
    suspend fun switchUser(targetUser: Int, mainUser: Int = 0): String? = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext null
        // First attempt to switch to the target user.  Capture any output
        // produced by the command; a successful switch normally yields no
        // output.  If output is present, propagate it as an error.
        val result = executeShell("am switch-user $targetUser")
        if (result?.isNotBlank() == true) {
            return@withContext result
        }
        // If the target user differs from the main user, prepare a polling
        // script that monitors when the device UI becomes inactive.  Once
        // inactive, the script sleeps briefly and then switches back to the
        // specified main user.  The entire script is wrapped in parentheses
        // and suffixed with `&` so that it runs asynchronously on the
        // device.  Joining commands with semicolons avoids spawning an
        // interactive shell.
        if (targetUser != mainUser) {
            val pollingScript = listOf(
                // Loop until the interactive flag is no longer true
                "while dumpsys input | grep -q \"Interactive = true\"; do",
                // Sleep a few seconds between polls to reduce load
                "sleep 3",
                "done",
                // Give the system a moment to fully idle
                "sleep 2",
                // Switch back to the main/owner user
                "am switch-user $mainUser"
            ).joinToString("; ")
            // Launch the polling script in a background subshell.  Using
            // parentheses groups the commands and `&` detaches them so the
            // shell returns immediately.  We explicitly avoid capturing
            // output from this invocation; the return value of executeShell
            // is disregarded.
            val fullCommand = "(" + pollingScript + ") &"
            executeShell(fullCommand)
        }
        return@withContext null
    }

    companion object {
        private const val TAG = "AdbHelper"
    }
}