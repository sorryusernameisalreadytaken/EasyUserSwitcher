package `in`.hridayan.ashell.userswitcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Simple activity invoked by launcher shortcuts to perform a user switch via
 * ADB.  This activity should be exported so that pinned and dynamic
 * shortcuts can trigger it from the launcher.  It reads the `user_id` and
 * `user_name` extras from the intent, calls [AdbHelper.switchUser] to
 * perform the switch and then finishes immediately.  No UI is shown.
 */
class UserSwitchShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targetId = intent.getIntExtra("user_id", -1)
        // Only attempt a switch if the ID is valid and not the owner (0).
        if (targetId > 0) {
            val helper = AdbHelper(this)
            lifecycleScope.launch {
                helper.switchUser(targetId, 0)
                // finish the activity once the command has been sent
                finish()
            }
        } else {
            // No valid target; simply finish
            finish()
        }
    }
}