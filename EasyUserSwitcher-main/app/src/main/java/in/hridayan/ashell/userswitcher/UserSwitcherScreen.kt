package `in`.hridayan.ashell.userswitcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.OutlinedTextField // removed unused
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
// Import the generated R class from the application package to resolve drawable resources
import `in`.hridayan.ashell.R
// import androidx.compose.foundation.layout.Arrangement // removed unused
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * A Compose screen that allows the user to pair with their own device's ADB daemon,
 * establish a wireless ADB connection and list available Android user profiles. Each
 * discovered profile can be tapped to temporarily switch to that profile via ADB.
 * When the device becomes idle (screen off), the script automatically returns to
 * the main user. This screen is designed to live within the AShellYou application
 * and reuses the [AdbHelper] from the userswitcher package.
 */
@Composable
fun UserSwitcherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Use the shared ADB helper which delegates to the global AdbConnectionManager
    val adbHelper = remember { AdbHelper(context) }

    // Keep track of status and loaded users. The status will reflect
    // whether we are connected, loading users, or any error messages.
    var status by rememberSaveable { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<Pair<Int, String>>()) }

    // Convenience to update the status from various callbacks.
    fun updateStatus(msg: String) {
        status = msg
    }

    // On first composition, set the initial status based on connection state.
    LaunchedEffect(Unit) {
        updateStatus(if (adbHelper.isConnected()) "Connected" else "No ADB connection")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Easy User Switcher",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Inform the user when no ADB connection is available. The rest of the
        // UI remains disabled until an ADB connection has been established via
        // Local ADB, wireless debugging or OTG in other parts of the app.
        val isConnected = adbHelper.isConnected()
        if (!isConnected) {
            Text(
                text = "No ADB connection. Please connect via Local ADB, Wireless debugging or OTG first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Action button to load users. Enabled only when we have an active
        // ADB connection. When clicked, it fetches the list of users via
        // `pm list users` and populates the users state.
        Button(
            onClick = {
                updateStatus("Loading users…")
                scope.launch {
                    val output = adbHelper.executeShell("pm list users")
                    if (output == null) {
                        updateStatus("Failed to list users")
                    } else {
                        val parsed = adbHelper.parseUsers(output)
                        users = parsed
                        updateStatus("${'$'}{parsed.size} users loaded")
                    }
                }
            },
            enabled = isConnected
        ) {
            Text("Load Users")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Status: ${'$'}status")

        Spacer(modifier = Modifier.height(16.dp))

        // List of users. Each row is clickable to switch user. The list is
        // displayed regardless of connection state – if users are loaded,
        // they remain visible. Tapping on a user triggers the switch script.
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(users) { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Only allow switching if currently connected
                            if (!adbHelper.isConnected()) {
                                updateStatus("No ADB connection to switch user")
                                return@clickable
                            }
                            scope.launch {
                                updateStatus("Switching to ${'$'}name (${'$'}id)…")
                                adbHelper.switchUser(id, 0)
                                updateStatus("Switch command sent for ${'$'}name")
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${'$'}name (${'$'}id)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}