package chat.stoat.internals

import android.util.Log
import chat.stoat.BuildConfig
import chat.stoat.StoatApplication
import chat.stoat.api.StoatJson
import chat.stoat.persistence.KVStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class DismodEmojiCatalog(
    val version: Int = 1,
    val emojis: List<DismodEmojiItem> = emptyList()
)

@Serializable
data class DismodEmojiItem(
    val id: String = "",
    val name: String = "",
    val shortcode: String = "",
    val category: String = "Reactions",
    val categoryEmoji: String = "✨",
    val mediaUrl: String = "",
    val previewUrl: String = "",
    val filename: String? = null,
    val format: String? = null,
    val sourceUrl: String? = null,
    val sha256: String? = null,
    val isAnimated: Boolean = true
) {
    val displayMediaUrl: String
        get() = mediaUrl.ifBlank { previewUrl }
}

object DismodEmojiManager {
    private const val TAG = "DismodEmojiManager"
    private const val NETLIFY_EMOJIS_URL = "https://adminofdismod.netlify.app/api/emojis"
    private const val GITHUB_EMOJIS_URL = "https://raw.githubusercontent.com/tonij7853-dotcom/demajk9022/main/community-assets/emojis/catalog.json"
    private const val STORAGE_KEY = "dismod_custom_emojis_v3"

    private val _emojisFlow = MutableStateFlow<List<DismodEmojiItem>>(emptyList())
    val emojisFlow: StateFlow<List<DismodEmojiItem>> = _emojisFlow.asStateFlow()

    private val shortcodeMap = ConcurrentHashMap<String, DismodEmojiItem>()
    private val urlMap = ConcurrentHashMap<String, DismodEmojiItem>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    init {
        loadBundled()
        syncCatalogAsync()
    }

    private fun loadBundled() {
        val app = StoatApplication.instance
        var loaded: List<DismodEmojiItem> = emptyList()
        try {
            val json = app.applicationContext.assets.open("metadata/dismod_emojis.json").use {
                it.reader().readText()
            }
            val catalog = StoatJson.decodeFromString(DismodEmojiCatalog.serializer(), json)
            loaded = catalog.emojis
            Log.d(TAG, "Loaded ${loaded.size} bundled emojis from assets")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bundled emojis: ${e.message}")
            loaded = emptyList()
        }
        if (loaded.isNotEmpty()) {
            updateList(loaded)
        }
    }

    fun syncCatalogAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            val app = StoatApplication.instance
            // 1. Check local storage cache
            try {
                val kvStorage = KVStorage(app.applicationContext)
                val cachedJson = kvStorage.get(STORAGE_KEY)
                if (!cachedJson.isNullOrBlank()) {
                    val catalog = StoatJson.decodeFromString(DismodEmojiCatalog.serializer(), cachedJson)
                    if (catalog.emojis.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${catalog.emojis.size} cached emojis from KVStorage")
                        updateList(catalog.emojis)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load cached emojis: ${e.message}")
            }

            // 2. Fetch live catalog from Netlify
            try {
                val finalUrl = "$NETLIFY_EMOJIS_URL?t=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "DismodAndroid/${BuildConfig.VERSION_NAME}")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            Log.d(TAG, "Fetched live emojis JSON: $body")
                            val catalog = StoatJson.decodeFromString(DismodEmojiCatalog.serializer(), body)
                            Log.i(TAG, "Successfully parsed ${catalog.emojis.size} emojis from Netlify")
                            updateList(catalog.emojis)
                            val kvStorage = KVStorage(app.applicationContext)
                            kvStorage.set(STORAGE_KEY, body)
                            return@launch
                        }
                    } else {
                        Log.w(TAG, "Failed to fetch emojis from Netlify: HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching emojis from Netlify", e)
            }

            // 3. Fallback to GitHub if Netlify failed
            try {
                val finalUrl = "$GITHUB_EMOJIS_URL?t=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "DismodAndroid/${BuildConfig.VERSION_NAME}")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val catalog = StoatJson.decodeFromString(DismodEmojiCatalog.serializer(), body)
                            if (catalog.emojis.isNotEmpty()) {
                                Log.i(TAG, "Fallback parsed ${catalog.emojis.size} emojis from GitHub")
                                updateList(catalog.emojis)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub fallback failed: ${e.message}")
            }
        }
    }

    private fun updateList(items: List<DismodEmojiItem>) {
        shortcodeMap.clear()
        urlMap.clear()
        for (item in items) {
            val cleanCode = item.shortcode.trim().removeSurrounding(":")
            shortcodeMap[cleanCode.lowercase()] = item
            shortcodeMap[":${cleanCode.lowercase()}:"] = item
            val targetUrl = item.displayMediaUrl
            if (targetUrl.isNotBlank()) {
                urlMap[targetUrl] = item
            }
            if (item.mediaUrl.isNotBlank()) {
                urlMap[item.mediaUrl] = item
            }
            if (item.previewUrl.isNotBlank()) {
                urlMap[item.previewUrl] = item
            }
        }
        _emojisFlow.value = items
    }

    fun getAllEmojis(): List<DismodEmojiItem> = _emojisFlow.value

    fun findEmojiByShortcode(code: String): DismodEmojiItem? {
        val clean = code.trim().removeSurrounding(":").lowercase()
        return shortcodeMap[clean]
    }

    fun findEmojiByUrl(url: String): DismodEmojiItem? = urlMap[url]

    fun isEmojiUrl(url: String): Boolean {
        if (urlMap.containsKey(url)) return true
        if (url.contains("cdn3.emoji.gg/emojis/") || url.contains("emoji.gg/emojis/")) return true
        if (url.contains("adminofdismod.netlify.app/api/raw/emoji-")) return true
        return false
    }

    fun search(query: String): List<DismodEmojiItem> {
        val q = query.trim().removeSurrounding(":").lowercase()
        if (q.isBlank()) return emptyList()
        return _emojisFlow.value.filter {
            it.shortcode.contains(q, ignoreCase = true) ||
            it.name.contains(q, ignoreCase = true) ||
            it.category.contains(q, ignoreCase = true)
        }
    }

    fun getPacks(): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        for (emoji in _emojisFlow.value) {
            if (seen.add(emoji.category)) {
                list.add(emoji.category to emoji.categoryEmoji)
            }
        }
        return list
    }

    fun formatMarkdown(emoji: DismodEmojiItem): String = "![${emoji.shortcode}](${emoji.displayMediaUrl})"
}
