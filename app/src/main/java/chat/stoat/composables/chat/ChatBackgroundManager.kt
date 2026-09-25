package chat.stoat.composables.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ChatBackgroundManager {
    // Reactive trigger for Compose recomposition when background changes
    var revision by mutableIntStateOf(0)
        private set

    private fun getDir(context: Context): File {
        val dir = File(context.filesDir, "chat_backgrounds")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getBackgroundFile(context: Context, channelId: String?): File? {
        val dir = getDir(context)
        if (channelId != null) {
            val channelFile = File(dir, "bg_${channelId}.jpg")
            if (channelFile.exists() && channelFile.length() > 0) {
                return channelFile
            }
        }
        val defaultFile = File(dir, "bg_default.jpg")
        if (defaultFile.exists() && defaultFile.length() > 0) {
            return defaultFile
        }
        return null
    }

    fun hasCustomBackground(context: Context, channelId: String?): Boolean {
        return getBackgroundFile(context, channelId) != null
    }

    suspend fun saveBackground(
        context: Context,
        channelId: String?,
        uri: Uri,
        forAllPrivateMessages: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getDir(context)
            val targetFile = if (forAllPrivateMessages || channelId == null) {
                File(dir, "bg_default.jpg")
            } else {
                File(dir, "bg_${channelId}.jpg")
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            withContext(Dispatchers.Main) {
                revision++
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeBackground(
        context: Context,
        channelId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = getDir(context)
            var deleted = false
            if (channelId != null) {
                val channelFile = File(dir, "bg_${channelId}.jpg")
                if (channelFile.exists()) {
                    deleted = channelFile.delete() || deleted
                }
            }
            val defaultFile = File(dir, "bg_default.jpg")
            if (defaultFile.exists()) {
                deleted = defaultFile.delete() || deleted
            }
            withContext(Dispatchers.Main) {
                revision++
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
