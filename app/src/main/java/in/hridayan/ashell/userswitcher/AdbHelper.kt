package `in`.hridayan.ashell.userswitcher

import android.content.Context
import android.util.Log
// Use the shared ADB connection manager provided by the rest of the app.
// The EasyAdbConnectionManager was a private implementation specific to the
// user‑switcher feature. To ensure the user switcher reuses the same
// underlying connection as the rest of the app (local ADB, wireless and OTG
// connections), we import the global manager from the shell.common package.
import `in`.hridayan.ashell.shell.common.data.adb.AdbConnectionManager
import `in`.hridayan.ashell.userswitcher.SettingsRepository
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
     * Switch to a target user.  The immediate switch is performed first
     * using `am switch-user <target>`.  If the target differs from the
     * owner (`mainUser`), a post‑switch script is scheduled to run in
     * the background.  The post‑switch behaviour is determined as follows:
     *
     *  * If the user has configured a custom command via
     *    [SettingsRepository.setCustomCommand], that command is executed
     *    with any `{id}` placeholders replaced by the target user ID.  The
     *    command runs asynchronously on the device without blocking the
     *    current ADB stream.
     *  * Otherwise, a default script polls `dumpsys input` every two
     *    seconds until the display is no longer interactive, then switches
     *    back to `mainUser` via `am switch-user`.
     *
     * Any output returned by the initial user switch is returned to the
     * caller so that errors can be surfaced in the UI.  The asynchronous
     * scripts do not return a value.
     */
    suspend fun switchUser(targetUser: Int, mainUser: Int = 0): String? = withContext(Dispatchers.IO) {
        if (!isConnected()) return@withContext null
        // Perform the immediate switch.  We capture output to report
        // potential errors (e.g. unknown user ID).
        val switchOutput = executeShell("am switch-user ${'$'}targetUser")
        // Schedule follow‑up actions when switching away from the main user.
        if (targetUser != mainUser) {
            val custom = SettingsRepository.getCustomCommand(context).trim()
            if (custom.isNotEmpty()) {
                // Substitute placeholder for user ID.  The user can include
                // shell operators (&&, ||, etc.) as desired.
                val replaced = custom.replace("{id}", targetUser.toString())
                val asyncCmd = "(" + replaced + ") &"
                executeShell(asyncCmd)
            } else {
                // Default behaviour: monitor interactive state and return to
                // owner.  Use a two‑second polling interval for
                // responsiveness.  We omit an extra sleep after the loop
                // since `dumpsys input` flips to false only when the screen
                // turns off.
                val script = "while dumpsys input | grep -q \"Interactive = true\"; do sleep 2; done && am switch-user ${'$'}mainUser"
                val asyncCmd = "(" + script + ") &"
                executeShell(asyncCmd)
            }
        }
        return@withContext switchOutput
    }

    companion object {
        private const val TAG = "AdbHelper"
    }
}