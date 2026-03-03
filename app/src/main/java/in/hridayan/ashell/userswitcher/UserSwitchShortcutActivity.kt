package `in`.hridayan.ashell.userswitcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Activity invoked from dynamic or pinned shortcuts. It receives the user ID
 * and optional name from the intent extras, triggers the user switch via
 * [AdbHelper.switchUser] and immediately finishes. This activity has no UI.
 */
class UserSwitchShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract extras; if the user ID is invalid then simply finish.
        val userId = intent?.getIntExtra("user_id", -1) ?: -1
        if (userId < 0) {
            finish()
            return
        }
        // Use applicationContext to avoid leaking the activity context.
        val helper = AdbHelper(applicationContext)
        // Launch the switch in a coroutine. We don't wait for completion.
        lifecycleScope.launch {
            helper.switchUser(userId, 0)
            // Finish the activity once the command has been sent.
            finish()
        }
    }
}