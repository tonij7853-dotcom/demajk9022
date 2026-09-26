package chat.stoat.internals

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object LocalMediaCache {
    private const val TAG = "LocalMediaCache"
    private const val MAX_CACHED_FILES = 100
    private val urlToFileMap = ConcurrentHashMap<String, File>()

    fun recordSentMedia(url: String, file: File) {
        if (!file.exists() || file.length() == 0L) return
        val cleanUrl = url.substringBefore("?")
        urlToFileMap[cleanUrl] = file
        urlToFileMap[url] = file
        Log.d(TAG, "Cached sent media for URL: $cleanUrl -> ${file.absolutePath} (${file.length()} bytes)")
        trimCacheIfNeeded(file.parentFile)
    }

    fun getFile(url: String): File? {
        val cleanUrl = url.substringBefore("?")
        val file = urlToFileMap[cleanUrl] ?: urlToFileMap[url]
        return if (file != null && file.exists() && file.length() > 0L) file else null
    }

    private fun trimCacheIfNeeded(dir: File?) {
        if (dir == null || !dir.exists()) return
        try {
            val files = dir.listFiles() ?: return
            if (files.size > MAX_CACHED_FILES) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - MAX_CACHED_FILES)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed trimming media cache: ${e.message}")
        }
    }
}
