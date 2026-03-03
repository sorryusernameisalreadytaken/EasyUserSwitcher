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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.hridayan.ashell.R
import kotlinx.coroutines.launch

/**
 * UserSwitcherScreen lists available Android user profiles and allows the user
 * to switch between them. It relies on an existing ADB connection established
 * elsewhere in the app. When no connection is present, the screen displays
 * a notice and disables interactions. Long‑pressing a user entry allows
 * creation of a pinned launcher shortcut for quick switching.
 */
@Composable
fun UserSwitcherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adbHelper = remember { AdbHelper(context) }

    var status by rememberSaveable { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<Pair<Int, String>>()) }

    fun updateStatus(msg: String) {
        status = msg
    }

    // Initialize status on first composition
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

        val isConnected = adbHelper.isConnected()
        if (!isConnected) {
            Text(
                text = "No ADB connection. Please connect via Local ADB, Wireless debugging or OTG first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Load users from pm list users
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
                        // Setup dynamic shortcuts for up to 4 users (skip owner)
                        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                        if (shortcutManager != null) {
                            val dynamicShortcuts = parsed.mapNotNull { (id, name) ->
                                if (id == 0) return@mapNotNull null
                                val shortcutId = "eus_user_$id"
                                val iconName = "ic_user_switcher_$id"
                                val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                val iconRes = if (resId != 0) resId else R.drawable.ic_user_switcher
                                android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                                    .setShortLabel(name)
                                    .setLongLabel("Switch to $name")
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
                        updateStatus("${parsed.size} users loaded")
                    }
                }
            },
            enabled = isConnected
        ) {
            Text("Load Users")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Status: $status")

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(users) { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (!adbHelper.isConnected()) {
                                    updateStatus("No ADB connection to switch user")
                                    return@combinedClickable
                                }
                                if (id == 0) {
                                    updateStatus("Owner account cannot be switched to from here")
                                    return@combinedClickable
                                }
                                scope.launch {
                                    updateStatus("Switching to $name ($id)…")
                                    adbHelper.switchUser(id, 0)
                                    updateStatus("Switch command sent for $name")
                                }
                            },
                            onLongClick = {
                                if (id == 0) return@combinedClickable
                                val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                                    val iconName = "ic_user_switcher_$id"
                                    val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                    val iconRes = if (resId != 0) resId else R.drawable.ic_user_switcher
                                    val shortcut = android.content.pm.ShortcutInfo.Builder(context, "eus_pin_user_$id")
                                        .setShortLabel(name)
                                        .setLongLabel("Switch to $name")
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
                        text = "$name ($id)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}