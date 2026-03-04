package `in`.hridayan.ashell.userswitcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Provides utilities for persisting and retrieving custom user icons.  When
 * a user selects an image to represent a profile, the resulting bitmap is
 * scaled and stored on the device's internal storage.  The absolute path
 * is saved via [SettingsRepository] for later retrieval.  Icons are named
 * deterministically using the user ID to avoid collisions.
 */
object CustomIconManager {
    /**
     * Save the provided bitmap as a PNG in the app's private files
     * directory.  The file name is derived from the user ID.  After
     * successful write the corresponding preference entry is updated.  The
     * returned string is the absolute path of the saved file.
     */
    fun saveCustomIcon(context: Context, id: Int, bitmap: Bitmap): String {
        val fileName = "icon_user_${'$'}id.png"
        val file = File(context.filesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        SettingsRepository.setCustomIconPath(context, id, file.absolutePath)
        return file.absolutePath
    }

    /**
     * Load a previously saved custom icon for the given user ID.  Returns
     * null if no custom icon is registered or if the file does not exist.
     */
    fun loadCustomIcon(context: Context, id: Int): Bitmap? {
        val path = SettingsRepository.getCustomIconPath(context, id) ?: return null
        val file = File(path)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else null
    }
}