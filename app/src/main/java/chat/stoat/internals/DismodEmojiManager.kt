package chat.stoat.internals

import android.content.Context
import chat.stoat.StoatApplication
import chat.stoat.api.StoatJson
import chat.stoat.persistence.KVStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class DismodEmojiCatalog(
    val version: Int = 1,
    val emojis: List<DismodEmojiItem> = emptyList()
)

@Serializable
data class DismodEmojiItem(
    val id: String,
    val name: String,
    val shortcode: String,
    val category: String = "Reactions",
    val categoryEmoji: String = "✨",
    val mediaUrl: String,
    val previewUrl: String = mediaUrl,
    val isAnimated: Boolean = true
)

object DismodEmojiManager {
    private const val NETLIFY_EMOJIS_URL = "https://adminofdismod.netlify.app/api/emojis"
    private const val GITHUB_EMOJIS_URL = "https://raw.githubusercontent.com/tonij7853-dotcom/demajk9022/main/community-assets/emojis/catalog.json"
    private const val STORAGE_KEY = "dismod_custom_emojis_v2"

    private val _emojisFlow = MutableStateFlow<List<DismodEmojiItem>>(emptyList())
    val emojisFlow: StateFlow<List<DismodEmojiItem>> = _emojisFlow.asStateFlow()

    private val shortcodeMap = ConcurrentHashMap<String, DismodEmojiItem>()
    private val urlMap = ConcurrentHashMap<String, DismodEmojiItem>()

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
        } catch (_: Exception) {
            loaded = emptyList()
        }
        updateList(loaded)
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
                    updateList(catalog.emojis)
                }
            } catch (_: Exception) {
            }

            // 2. Fetch live catalog from Netlify
            val client = HttpClient()
            var fetchedJson: String? = null

            try {
                val response = client.get(NETLIFY_EMOJIS_URL)
                fetchedJson = response.bodyAsText()
            } catch (_: Exception) {
                // Fallback to github if Netlify is temporarily unreachable
                try {
                    val response = client.get(GITHUB_EMOJIS_URL)
                    fetchedJson = response.bodyAsText()
                } catch (_: Exception) {
                    // Both offline, keep cached
                }
            }

            if (!fetchedJson.isNullOrBlank()) {
                try {
                    val catalog = StoatJson.decodeFromString(DismodEmojiCatalog.serializer(), fetchedJson)
                    updateList(catalog.emojis)
                    val kvStorage = KVStorage(app.applicationContext)
                    kvStorage.set(STORAGE_KEY, fetchedJson)
                } catch (_: Exception) {
                    // ignore json error
                }
            }
            client.close()
        }
    }

    private fun updateList(items: List<DismodEmojiItem>) {
        shortcodeMap.clear()
        urlMap.clear()
        for (item in items) {
            val cleanCode = item.shortcode.trim().removeSurrounding(":")
            shortcodeMap[cleanCode.lowercase()] = item
            shortcodeMap[":${cleanCode.lowercase()}:"] = item
            urlMap[item.mediaUrl] = item
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

    fun formatMarkdown(emoji: DismodEmojiItem): String = "![${emoji.shortcode}](${emoji.mediaUrl})"
}
