package `in`.hridayan.ashell.userswitcher

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import `in`.hridayan.ashell.R
import `in`.hridayan.ashell.userswitcher.UserSwitchShortcutActivity
import android.content.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// A palette of colours used to visually distinguish different user profiles.  The
// colours are selected from the Material design palette and cycle when the
// number of users exceeds the palette length.  This deterministic mapping
// ensures that a given user ID always maps to the same colour.
private val userColors: List<Color> = listOf(
    Color(0xFFE57373), // Red
    Color(0xFFF06292), // Pink
    Color(0xFFBA68C8), // Purple
    Color(0xFF9575CD), // Deep Purple
    Color(0xFF7986CB), // Indigo
    Color(0xFF64B5F6), // Blue
    Color(0xFF4FC3F7), // Light Blue
    Color(0xFF4DD0E1), // Cyan
    Color(0xFF4DB6AC), // Teal
    Color(0xFF81C784), // Green
    Color(0xFFAED581), // Light Green
    Color(0xFFFFD54F), // Amber
    Color(0xFFFFB74D), // Orange
    Color(0xFFFF8A65), // Deep Orange
    Color(0xFFD4E157)  // Lime
)

/**
 * Determine a colour for the supplied user ID.  The ID is mapped to an
 * index in the colour palette using its absolute value to support both
 * positive and negative IDs (although user IDs are typically non‑negative).
 */
fun colorForUser(id: Int): Color {
    val index = abs(id) % userColors.size
    return userColors[index]
}

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
                            // Skip owner (0) for dynamic shortcuts
                            if (id == 0) return@mapNotNull null
                            val shortcutId = "eus_user_$id"

                            // Use one of the bundled, pre‑colored icons for shortcuts.
                            // IMPORTANT: Android (esp. 13+) rejects ShortcutInfo icons that have
                            // a runtime tint applied (IllegalArgumentException: "Icons with tints are not supported").
                            // Therefore we ship 32 separate drawable PNGs:
                            //   ic_user_switcher_color_01 .. ic_user_switcher_color_32
                            // and deterministically map user IDs to one of them.
                            val iconIndex = (abs(id) % 32) + 1
                            val iconName = String.format("ic_user_switcher_color_%02d", iconIndex)
                            val iconResId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                            val icon = if (iconResId != 0) {
                                android.graphics.drawable.Icon.createWithResource(context, iconResId)
                            } else {
                                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_user_switcher)
                            }

                            val intent = Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                putExtra("user_id", id)
                                putExtra("user_name", name)
                            }
                            android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                                .setShortLabel(name)
                                .setLongLabel("Switch to $name")
                                .setIcon(icon)
                                .setIntent(intent)
                                .build()
                        }
                        // Replace all existing dynamic shortcuts with our updated list (max 4).
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

        // List of loaded users. Each item supports tap and long‑press.
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(users) { (id, name, running) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Colour the background of each user row using a translucent
                        // version of the assigned colour.  This helps users mentally
                        // map colours to profiles without overwhelming the UI.
                        .background(colorForUser(id).copy(alpha = 0.15f))
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
                                    updateStatus("Switching to $name ($id)…")
                                    val result = adbHelper.switchUser(id, 0)
                                    if (result != null && result.isNotBlank()) {
                                        // Show any error output from the command
                                        updateStatus(result.trim())
                                    } else {
                                        updateStatus("Switch command sent for $name")
                                    }
                                }
                            },
                            onLongClick = {
                                // Request a pinned shortcut for this user (except owner)
                                if (id == 0) return@combinedClickable
                                val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                                if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                                // On Android 13+ tinted icons for pinned shortcuts are prohibited and
                                // cause IllegalArgumentException.  Use the untinted base icon to
                                // create a pinned shortcut.  Colour cues are still available in
                                // the in‑app list view.
                                    // Use pre‑colored icons for pinned shortcuts as well (no runtime tint).
                                    val iconIndex = (abs(id) % 32) + 1
                                    val iconName = String.format("ic_user_switcher_color_%02d", iconIndex)
                                    val iconResId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
                                    val baseIcon = if (iconResId != 0) {
                                        android.graphics.drawable.Icon.createWithResource(context, iconResId)
                                    } else {
                                        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_user_switcher)
                                    }
                                    val shortcut = android.content.pm.ShortcutInfo.Builder(
                                        context,
                                        "eus_pin_user_${'$'}id"
                                    )
                                        .setShortLabel(name)
                                        .setLongLabel("Switch to ${'$'}name")
                                        .setIcon(baseIcon)
                                        .setIntent(
                                            Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                                action = Intent.ACTION_VIEW
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
                        // Bold the text of running users to make them stand out
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (running) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }
            }
        }

        // Periodically refresh the users list while this screen is active.  This
        // ensures the running state is updated in near‑real time and that
        // newly created or removed profiles show up without requiring manual
        // reloads.  The effect loops every few seconds as long as the
        // coroutine is active and the ADB connection is alive.  We avoid
        // updating the status text here to prevent flickering; only the
        // list itself is refreshed.
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(5_000)
                if (adbHelper.isConnected()) {
                    val output = adbHelper.executeShell("pm list users")
                    if (output != null) {
                        val parsed = adbHelper.parseUsers(output)
                        users = parsed
                    }
                }
            }
        }
    }
}