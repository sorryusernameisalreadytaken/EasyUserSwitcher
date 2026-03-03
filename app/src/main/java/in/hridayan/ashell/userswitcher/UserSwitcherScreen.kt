package `in`.hridayan.ashell.userswitcher

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.hridayan.ashell.R
import kotlinx.coroutines.launch

/**
 * A Compose screen that lists available Android user profiles and allows
 * switching between them via ADB. The screen assumes that an ADB connection
 * has already been established elsewhere in the app (e.g. via Local ADB,
 * Wireless debugging or OTG). When no connection is present, the Load
 * Users action and tap targets are disabled and an informative message
 * is shown. On long press, the user can create a pinned launcher shortcut
 * for the selected profile.
 */
@Composable
fun UserSwitcherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Shared ADB helper delegating to the global connection manager
    val adbHelper = remember { AdbHelper(context) }

    // UI state: status message and list of users. Use rememberSaveable for
    // users as well so the list persists across navigation and configuration
    // changes. Each user entry now also includes whether the user is currently
    // running.
    var status by rememberSaveable { mutableStateOf("") }
    var users by rememberSaveable { mutableStateOf(listOf<Triple<Int, String, Boolean>>()) }

    // Helper to update the status text
    fun updateStatus(msg: String) {
        status = msg
    }

    // Set initial status based on connection state. Don't override an
    // existing status if the screen is recreated while a list is loaded.
    LaunchedEffect(Unit) {
        if (status.isBlank()) {
            updateStatus(if (adbHelper.isConnected()) "Connected" else "No ADB connection")
        }
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

        val isConnected = adbHelper.isConnected()
        // Show a hint if not connected
        if (!isConnected) {
            Text(
                text = "No ADB connection. Please connect via Local ADB, Wireless debugging or OTG first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Button to fetch users
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
                        // Create dynamic shortcuts for up to four users (excluding owner)
                        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                        if (shortcutManager != null) {
                            val dynamicShortcuts = parsed.mapNotNull { (id, name, _) ->
                                if (id == 0) return@mapNotNull null
                                val shortcutId = "eus_user_${'$'}id"
                                val iconName = "ic_user_switcher_${'$'}id"
                                val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                val iconRes = if (resId != 0) resId else R.drawable.ic_user_switcher
                                android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                                    .setShortLabel(name)
                                    .setLongLabel("Switch to ${'$'}name")
                                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, iconRes))
                                    .setIntent(
                                        android.content.Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                            action = android.content.Intent.ACTION_VIEW
                                            putExtra("user_id", id)
                                            putExtra("user_name", name)
                                        }
                                    )
                                    .build()
                            }
                            shortcutManager.dynamicShortcuts = dynamicShortcuts.take(4)
                        }
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

        // List of loaded users. Each item supports tap and long‑press.
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(users) { (id, name, running) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                // Only attempt switching if connected
                                if (!adbHelper.isConnected()) {
                                    updateStatus("No ADB connection to switch user")
                                    return@combinedClickable
                                }
                                // Do not attempt to switch to owner via UI
                                if (id == 0) {
                                    updateStatus("Owner account cannot be switched to from here")
                                    return@combinedClickable
                                }
                                scope.launch {
                                    updateStatus("Switching to ${'$'}name (${'$'}id)…")
                                    val result = adbHelper.switchUser(id, 0)
                                    if (result != null && result.isNotBlank()) {
                                        // Show any error output from the command
                                        updateStatus(result.trim())
                                    } else {
                                        updateStatus("Switch command sent for ${'$'}name")
                                    }
                                }
                            },
                            onLongClick = {
                                // Request a pinned shortcut for this user (except owner)
                                if (id == 0) return@combinedClickable
                                val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                                    val iconName = "ic_user_switcher_${'$'}id"
                                    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                    val iconRes = if (resId != 0) resId else R.drawable.ic_user_switcher
                                    val shortcut = android.content.pm.ShortcutInfo.Builder(context, "eus_pin_user_${'$'}id")
                                        .setShortLabel(name)
                                        .setLongLabel("Switch to ${'$'}name")
                                        .setIcon(android.graphics.drawable.Icon.createWithResource(context, iconRes))
                                        .setIntent(
                                            android.content.Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                                action = android.content.Intent.ACTION_VIEW
                                                putExtra("user_id", id)
                                                putExtra("user_name", name)
                                            }
                                        )
                                        .build()
                                    shortcutManager.requestPinShortcut(shortcut, null)
                                }
                            }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append(name)
                            append(" (" + id + ")")
                            if (running) append(" – running")
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}