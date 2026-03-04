package `in`.hridayan.ashell.userswitcher

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import `in`.hridayan.ashell.R
import `in`.hridayan.ashell.userswitcher.CustomIconManager
import `in`.hridayan.ashell.userswitcher.UserSwitchShortcutActivity
import `in`.hridayan.ashell.userswitcher.SettingsRepository
import android.content.Intent
import android.content.pm.ShortcutInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.Image

/**
 * Load a coloured launcher icon for the given user ID.  This helper prefers
 * numbered icons (ic_user_switcher_1 .. ic_user_switcher_32) when available.
 * These icons include both a coloured background and the user ID overlay.  If
 * the numbered resource is not found or the ID exceeds the bundle, the icon
 * falls back to one of the 32 colour‑only images (ic_user_switcher_color_01 .. _32) by cycling the ID via modulo.  If neither resource can be resolved,
 * a generic fallback icon is returned.  When a resource is found it is
 * converted into a Bitmap and wrapped via [Icon.createWithBitmap] to ensure
 * that coloured icons are preserved on launchers that restrict tinted
 * resources (e.g. Android 13 pinned shortcuts).
 */
private fun loadUserShortcutIcon(context: Context, id: Int): Icon {
    val resources = context.resources
    val packageName = context.packageName
    // If a custom icon has been saved for this user, load it first.  Custom
    // icons override the bundled icons and preserve user‑chosen imagery.  If
    // none exists, fall back to numbered and colour icons.
    val customBitmap = CustomIconManager.loadCustomIcon(context, id)
    if (customBitmap != null) {
        return Icon.createWithBitmap(customBitmap)
    }
    // Prefer a numbered icon (ic_user_switcher_<id>) when the ID is within our
    // bundle.  These resources encode both a coloured background and the user
    // number.  Resource identifiers return 0 when the resource is missing.
    val numberResId = if (id in 1..32) {
        val name = "ic_user_switcher_${'$'}id"
        resources.getIdentifier(name, "drawable", packageName)
    } else 0
    // If no numbered resource exists, choose a colour‑only icon.  We cycle the
    // ID through 1..32 so that each user consistently maps to one of the
    // prepared assets.  The modulo arithmetic is adjusted to handle the
    // boundary case where id is a multiple of 32 (e.g. 32 → 32 rather than 1).
    val iconResId = if (numberResId != 0) {
        numberResId
    } else {
        // ((id - 1) % 32) yields 0‑31; adding 32 before modulo ensures
        // non‑negative results for negative IDs.
        val idx = (((id - 1) % 32) + 32) % 32 + 1
        val fallbackName = String.format("ic_user_switcher_color_%02d", idx)
        resources.getIdentifier(fallbackName, "drawable", packageName)
    }
    if (iconResId != 0) {
        val drawable = ContextCompat.getDrawable(context, iconResId)
        if (drawable != null) {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            return Icon.createWithBitmap(bitmap)
        }
    }
    return Icon.createWithResource(context, `in`.hridayan.ashell.R.drawable.ic_user_switcher)
}

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

    // Version counter for custom icons.  Each time a user selects
    // a new icon the counter is incremented.  Compose will recompute
    // remembered values that depend on this counter, ensuring that
    // updated icons are reloaded for individual list items.  We use
    // rememberSaveable so that the version survives configuration
    // changes but resets when the screen is recreated.
    var iconVersion by rememberSaveable { mutableStateOf(0) }

    // Dialog state for settings.  When true, a modal dialog is shown to
    // configure the post‑switch command.  We hold the pending command
    // separately to avoid writing to preferences until the user confirms.
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingUserId by remember { mutableStateOf<Int?>(null) }
    var pendingUserName by remember { mutableStateOf<String?>(null) }
    // Launcher to pick images for custom icons.  When a user selects an
    // image the callback crops and scales it, saves it to internal
    // storage and requests a pinned shortcut with the resulting bitmap.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val uid = pendingUserId
        val uname = pendingUserName
        if (uri != null && uid != null && uname != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val original = BitmapFactory.decodeStream(input)
                    if (original != null) {
                        val size = kotlin.math.min(original.width, original.height)
                        val left = (original.width - size) / 2
                        val top = (original.height - size) / 2
                        // Crop to a square
                        val square = Bitmap.createBitmap(original, left, top, size, size)
                        // Scale to a reasonable launcher icon size (256×256 px)
                        val finalBitmap = Bitmap.createScaledBitmap(square, 256, 256, true)
                        // Persist the icon and update UI.  After saving we bump
                        // the icon version and rebuild the user list so that
                        // Compose reloads only the affected entries.  Without
                        // this, Compose may reuse the same Bitmap for all
                        // users, causing every row to show the most recently
                        // selected icon.
                        CustomIconManager.saveCustomIcon(context, uid, finalBitmap)
                        iconVersion++
                        users = users.map { Triple(it.first, it.second, it.third) }
                        // Build and request the pinned shortcut
                        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                            val icon = Icon.createWithBitmap(finalBitmap)
                            val intent = Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                putExtra("user_id", uid)
                                putExtra("user_name", uname)
                            }
                            val shortcut = ShortcutInfo.Builder(context, "eus_pin_user_${'$'}uid")
                                .setShortLabel(uname)
                                .setLongLabel("Switch to ${'$'}uname")
                                .setIcon(icon)
                                .setIntent(intent)
                                .build()
                            shortcutManager.requestPinShortcut(shortcut, null)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore errors during image decoding or cropping
            }
        }
        pendingUserId = null
        pendingUserName = null
    }

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

    // Automatically load the user list when the screen is first displayed
    // and a connection to ADB is available.  We only perform this initial load
    // when the current list is empty to avoid repeatedly reloading on
    // recomposition.  The same sorting and shortcut creation logic used by the
    // reload button is applied here.
    LaunchedEffect(Unit) {
        // Only perform the initial load once, when no users are currently loaded
        // and the ADB connection is active.  We query the connection on demand
        // to avoid relying on an outer scoped variable that may not exist.
        if (users.isEmpty() && adbHelper.isConnected()) {
            updateStatus("Loading users…")
            val output = adbHelper.executeShell("pm list users")
            if (output == null) {
                updateStatus("Failed to list users")
            } else {
                val parsed = adbHelper.parseUsers(output)
                users = parsed.sortedWith(
                    compareBy<Triple<Int, String, Boolean>> {
                        when {
                            it.first == 0 -> 0
                            it.third -> 1
                            else -> 2
                        }
                    }.thenBy { it.first }
                )
                val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                if (shortcutManager != null) {
                    shortcutManager.removeAllDynamicShortcuts()
                    val dynamicShortcuts = parsed.mapNotNull { (id, name, _) ->
                        if (id == 0) return@mapNotNull null
                        val shortcutId = "eus_user_${'$'}id"
                        val icon = loadUserShortcutIcon(context, id)
                        val intent = Intent(context, UserSwitchShortcutActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            putExtra("user_id", id)
                            putExtra("user_name", name)
                        }
                        android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                            .setShortLabel(name)
                            .setLongLabel("Switch to ${'$'}name")
                            .setIcon(icon)
                            .setIntent(intent)
                            .build()
                    }
                    shortcutManager.dynamicShortcuts = dynamicShortcuts.take(4)
                }
                updateStatus("${parsed.size} users loaded")
            }
        }
    }

    // Wrap the main UI in a Box so that the settings button can be placed
    // independently of the header.  This helps avoid overlap with notches
    // or status bars by moving the button to the bottom‑right corner.  The
    // Column still contains the primary content.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header only displays the title.  The settings button is now
            // positioned separately at the bottom of the screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Easy User Switcher",
                    style = MaterialTheme.typography.titleLarge
                )
            }

        Spacer(modifier = Modifier.height(16.dp))

        // Show settings dialog when requested.  The dialog allows the user to
        // specify a custom ADB command to run after switching.  Use {id} as
        // a placeholder for the user ID.  The command is saved to
        // SharedPreferences on confirmation.
        if (showSettingsDialog) {
            var commandText by remember { mutableStateOf(SettingsRepository.getCustomCommand(context)) }
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Benutzerdefinierter ADB-Befehl") },
                text = {
                    Column {
                        Text("Dieser Befehl wird nach dem Benutzerwechsel ausgeführt. Verwenden Sie {id} als Platzhalter für die User‑ID.")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = commandText,
                            onValueChange = { commandText = it },
                            label = { Text("ADB-Befehl") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        SettingsRepository.setCustomCommand(context, commandText)
                        showSettingsDialog = false
                    }) {
                        Text("Speichern")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showSettingsDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        val isConnected = adbHelper.isConnected()
        // Show a hint if not connected
        if (!isConnected) {
            Text(
                text = "No ADB connection. Please connect via Local ADB, Wireless debugging or OTG first.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Button to fetch or reload users
        Button(
            onClick = {
                updateStatus("Loading users…")
                scope.launch {
                    val output = adbHelper.executeShell("pm list users")
                    if (output == null) {
                        updateStatus("Failed to list users")
                    } else {
                        val parsed = adbHelper.parseUsers(output)
                        // Sort users so that the owner (ID 0) is always first, followed by
                        // running profiles and then the remainder ordered by ascending ID.
                        users = parsed.sortedWith(
                            compareBy<Triple<Int, String, Boolean>> {
                                when {
                                    it.first == 0 -> 0
                                    it.third -> 1
                                    else -> 2
                                }
                            }.thenBy { it.first }
                        )
                        val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
                        if (shortcutManager != null) {
                            // Remove any existing dynamic shortcuts before creating new ones. This avoids
                            // stale entries such as "E‑Auto" remaining in the launcher.
                            shortcutManager.removeAllDynamicShortcuts()
                            val dynamicShortcuts = parsed.mapNotNull { (id, name, _) ->
                                // Skip owner (0) for dynamic shortcuts
                                if (id == 0) return@mapNotNull null
                                val shortcutId = "eus_user_${'$'}id"
                                val icon = loadUserShortcutIcon(context, id)
                                val intent = Intent(context, UserSwitchShortcutActivity::class.java).apply {
                                    action = Intent.ACTION_VIEW
                                    putExtra("user_id", id)
                                    putExtra("user_name", name)
                                }
                                android.content.pm.ShortcutInfo.Builder(context, shortcutId)
                                    .setShortLabel(name)
                                    .setLongLabel("Switch to ${'$'}name")
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
            Text("Reload Users")
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
                                // When long‑pressing a profile (except the owner), prompt the user
                                // to select an image to use as the launcher shortcut icon.  The
                                // selected image is cropped, scaled and persisted, and a pinned
                                // shortcut is then created using that image.  Requesting the
                                // image every time avoids having to build a separate icon
                                // management UI.
                                if (id == 0) return@combinedClickable
                                pendingUserId = id
                                pendingUserName = name
                                pickImageLauncher.launch("image/*")
                            }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Display a custom icon if one exists; otherwise show a generic person icon.
                    // Load a custom icon if one exists.  The icon is
                    // remembered based on both the user ID and the
                    // iconVersion counter.  When a new icon is saved, the
                    // version increments and Compose reloads the bitmap for
                    // each row individually.  This prevents the same
                    // Bitmap instance from being reused across all rows.
                    val customIconBitmap = remember(id, iconVersion) {
                        CustomIconManager.loadCustomIcon(context, id)
                    }
                    if (customIconBitmap != null) {
                        Image(
                            bitmap = customIconBitmap.asImageBitmap(),
                            contentDescription = "Icon for ${'$'}name",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "User",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            tint = Color.Unspecified
                        )
                    }
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
                        // Maintain the same sort order during periodic refreshes: owner first,
                        // then running users, then all others by ID.
                        users = parsed.sortedWith(
                            compareBy<Triple<Int, String, Boolean>> {
                                when {
                                    it.first == 0 -> 0
                                    it.third -> 1
                                    else -> 2
                                }
                            }.thenBy { it.first }
                        )
                    }
                }
            }
        }
        }

        // Settings button anchored to the bottom right of the screen.  This
        // button opens the settings dialog when tapped.
        IconButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            onClick = { showSettingsDialog = true }
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings"
            )
        }
    } // end Box
} // end composable