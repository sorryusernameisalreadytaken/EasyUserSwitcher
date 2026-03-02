package `in`.hridayan.ashell.userswitcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.Arrangement
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
    val adbHelper = remember { AdbHelper(context) }

    var host by rememberSaveable { mutableStateOf("127.0.0.1") }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("5555") }
    var status by rememberSaveable { mutableStateOf("not connected") }
    var users by remember { mutableStateOf(listOf<Pair<Int, String>>()) }

    // Helper to update the status text. This runs on the UI thread because
    // Compose state updates are thread‑confined.
    fun updateStatus(msg: String) {
        status = msg
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

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pairingPort,
            onValueChange = { pairingPort = it },
            label = { Text("Pairing Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pairingCode,
            onValueChange = { pairingCode = it },
            label = { Text("Pairing Code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = connectPort,
            onValueChange = { connectPort = it },
            label = { Text("Connect Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Row of actions: Pair, Connect, Load Users
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                // Validate pairing inputs
                val port = pairingPort.toIntOrNull()
                if (pairingPort.isBlank() || pairingCode.isBlank() || port == null) {
                    updateStatus("Please enter valid pairing port and code")
                    return@Button
                }
                updateStatus("Pairing…")
                scope.launch {
                    val success = adbHelper.pair(
                        host.ifBlank { "127.0.0.1" },
                        port,
                        pairingCode.trim()
                    )
                    updateStatus(if (success) "Pairing successful" else "Pairing failed")
                }
            }) {
                Text("Pair")
            }

            Button(onClick = {
                val port = connectPort.toIntOrNull() ?: 5555
                updateStatus("Connecting…")
                scope.launch {
                    val connected = adbHelper.connect(
                        host.ifBlank { "127.0.0.1" },
                        port
                    )
                    updateStatus(if (connected) "Connected" else "Already connected or failed")
                }
            }) {
                Text("Connect")
            }

            Button(onClick = {
                if (!adbHelper.isConnected()) {
                    updateStatus("Connect to ADB first")
                    return@Button
                }
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
            }) {
                Text("Load Users")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Status: ${'$'}status")

        Spacer(modifier = Modifier.height(16.dp))

        // List of users. Each row is clickable to switch user.
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(users) { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
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