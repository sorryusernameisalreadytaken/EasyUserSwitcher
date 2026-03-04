package `in`.hridayan.ashell.userswitcher

import android.content.Context

/**
 * A small persistence helper for storing user‑defined settings such as the
 * post‑switch ADB command and mappings between user IDs and custom icon file
 * paths.  Preferences are stored in a private SharedPreferences file.  See
 * [getCustomCommand] and [setCustomCommand] for managing the shell command
 * executed after switching profiles, and [getCustomIconPath]/
 * [setCustomIconPath] for storing the absolute path to a saved icon for a
 * particular user ID.
 */
object SettingsRepository {
    private const val PREFS_NAME = "eus_settings"
    private const val KEY_CUSTOM_COMMAND = "custom_command"

    /**
     * Retrieve the custom ADB command configured by the user.  An empty
     * string indicates that no custom command is set.  Callers should
     * replace occurrences of `{id}` in the returned string with the actual
     * user ID before execution.
     */
    fun getCustomCommand(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_COMMAND, "") ?: ""

    /**
     * Persist a custom command for later retrieval.  The command may be
     * arbitrary shell code and may include the placeholder `{id}`, which
     * will be substituted with the actual user ID when the command is
     * executed.  Passing an empty string clears the current setting.
     */
    fun setCustomCommand(context: Context, command: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_COMMAND, command)
            .apply()
    }

    /**
     * Construct the preference key used to store a custom icon path for the
     * given user ID.  Keys are namespaced by the ID to avoid collisions.
     */
    private fun iconKey(id: Int): String = "custom_icon_${'$'}id"

    /**
     * Retrieve the file system path of a custom icon associated with the
     * specified user ID, or null if no custom icon has been saved.  Paths
     * are stored as absolute strings and are not validated on retrieval.
     */
    fun getCustomIconPath(context: Context, id: Int): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(iconKey(id), null)

    /**
     * Store the absolute file system path of a custom icon for a given
     * user ID.  The caller is responsible for ensuring the file exists and
     * is accessible.  Persisting the path allows the app to reload the
     * custom image across sessions.  Passing null will remove the mapping.
     */
    fun setCustomIconPath(context: Context, id: Int, path: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (path == null) {
            prefs.remove(iconKey(id))
        } else {
            prefs.putString(iconKey(id), path)
        }
        prefs.apply()
    }
}