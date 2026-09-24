package chat.stoat.sheets

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.stoat.api.StoatJson
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.api.StoatAPI
import chat.stoat.persistence.KVStorage
import chat.stoat.ui.theme.ClaudeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val NETLIFY_GIF_CATALOG_URL =
    "https://cheerful-pothos-8d3ee6.netlify.app/api/catalog"
private const val NETLIFY_GIF_ASSET_PREFIX =
    "https://cheerful-pothos-8d3ee6.netlify.app/api/raw/"
private const val LEGACY_GIF_CATALOG_URL =
    "https://raw.githubusercontent.com/tonij7853-dotcom/demajk9022/main/community-assets/gifs/catalog.json"
private const val LEGACY_GIF_ASSET_PREFIX =
    "https://raw.githubusercontent.com/tonij7853-dotcom/demajk9022/main/community-assets/gifs/"
private const val MAX_CATALOG_BYTES = 512 * 1024
private const val MAX_GIF_BYTES = 15 * 1024 * 1024

@Serializable
data class GifCatalog(
    val gifs: List<GifItem> = emptyList()
)

@Serializable
data class GifItem(
    val id: String,
    val title: String,
    val category: String = "Other",
    val categoryEmoji: String = "🙂",
    val tags: List<String> = emptyList(),
    val mediaUrl: String,
    val previewUrl: String = mediaUrl
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(
    onDismissRequest: () -> Unit,
    onGifSelected: (Uri) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var gifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    val accountId = StoatAPI.selfId
    val kvStorage = remember(context) { KVStorage(context) }

    LaunchedEffect(accountId) {
        favorites = accountId?.let { userId ->
            kvStorage.get("gifFavorites:$userId")
                ?.split(',')
                ?.filter { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
                ?.toSet()
        }.orEmpty()
        if (selectedCategory == "Favorites" && favorites.isEmpty()) selectedCategory = "All"
    }

    LaunchedEffect(refreshToken) {
        isLoading = true
        errorMessage = null
        try {
            gifs = withContext(Dispatchers.IO) { fetchGifCatalog() }
        } catch (_: Exception) {
            gifs = emptyList()
            errorMessage = "Couldn't load the GIF catalog. Check your connection and try again."
        } finally {
            isLoading = false
        }
    }

    val categories = remember(gifs) {
        listOf("All", "Favorites") + gifs.map { it.category.trim().ifEmpty { "Other" } }
            .distinct()
            .sortedBy { it.lowercase() }
            .filterNot { it == "All" }
    }
    val filteredGifs = remember(gifs, searchQuery, selectedCategory, favorites) {
        gifs.filter { gif ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Favorites" -> gif.id in favorites
                else -> gif.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                gif.title.contains(searchQuery, ignoreCase = true) ||
                gif.category.contains(searchQuery, ignoreCase = true) ||
                gif.categoryEmoji.contains(searchQuery, ignoreCase = true) ||
                gif.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    fun downloadAndSelectGif(gif: GifItem) {
        if (isDownloading) return
        isDownloading = true
        errorMessage = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadGif(gif, context.cacheDir) }
                onGifSelected(Uri.fromFile(file))
                onDismissRequest()
            } catch (_: Exception) {
                errorMessage = "Couldn't prepare this GIF. Try another one."
            } finally {
                isDownloading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(ClaudeTokens.Shapes.pill)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "GIFs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search GIFs...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                singleLine = true,
                shape = ClaudeTokens.Shapes.composerShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            if (categories.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    when (category) {
                                        "All" -> "✨ All GIFs"
                                        "Favorites" -> "💜 Favorites"
                                        else -> {
                                            val emoji = gifs.firstOrNull { it.category.equals(category, ignoreCase = true) }
                                                ?.categoryEmoji ?: "🙂"
                                            "$emoji $category"
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading || isDownloading -> Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                errorMessage != null -> Column(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { refreshToken++ }) { Text("Retry") }
                }

                filteredGifs.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (gifs.isEmpty()) "No GIFs have been added yet."
                        else "No GIFs match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                ) {
                    items(filteredGifs, key = { it.id }) { gif ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1.2f)
                                    .clip(ClaudeTokens.Shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                RemoteImage(
                                    url = gif.previewUrl,
                                    description = gif.title,
                                    contentScale = ContentScale.Crop,
                                    allowAnimation = true,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(enabled = !isDownloading) { downloadAndSelectGif(gif) }
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                        .clickable {
                                            val updated = favorites.toMutableSet().apply {
                                                if (!add(gif.id)) remove(gif.id)
                                            }.toSet()
                                            favorites = updated
                                            accountId?.let { userId ->
                                                scope.launch { kvStorage.set("gifFavorites:$userId", updated.sorted().joinToString(",")) }
                                            }
                                        }
                                        .semantics {
                                            contentDescription = if (gif.id in favorites) "Remove ${gif.title} from favorites" else "Add ${gif.title} to favorites"
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (gif.id in favorites) "♥" else "♡",
                                        color = if (gif.id in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Text(
                                    text = gif.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .clickable(enabled = !isDownloading) { downloadAndSelectGif(gif) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fetchGifCatalog(): List<GifItem> {
    return try {
        fetchCatalogFromUrl(NETLIFY_GIF_CATALOG_URL)
    } catch (_: Exception) {
        try {
            fetchCatalogFromUrl(LEGACY_GIF_CATALOG_URL)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private fun fetchCatalogFromUrl(catalogUrl: String): List<GifItem> {
    val connection = (URL(catalogUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-cache")
    }

    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("GIF catalog request failed")
        }
        if (connection.contentLengthLong > MAX_CATALOG_BYTES) {
            throw IllegalStateException("GIF catalog is too large")
        }
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_CATALOG_BYTES) throw IllegalStateException("GIF catalog is too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

        return StoatJson.decodeFromString<GifCatalog>(bytes.toString(Charsets.UTF_8))
            .gifs
            .asSequence()
            .filter { gif ->
                gif.id.matches(Regex("[A-Za-z0-9_-]{1,80}")) &&
                    gif.title.isNotBlank() && gif.title.length <= 80 &&
                    gif.category.length <= 32 && gif.categoryEmoji.length <= 8 && gif.tags.size <= 20 &&
                    isAllowedGifUrl(gif.mediaUrl) && isAllowedGifUrl(gif.previewUrl)
            }
            .distinctBy { it.id }
            .take(300)
            .toList()
    } finally {
        connection.disconnect()
    }
}

private fun downloadGif(gif: GifItem, cacheDir: File): File {
    require(isAllowedGifUrl(gif.mediaUrl)) { "GIF URL is not in the owner catalog" }
    val connection = (URL(gif.mediaUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }

    try {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("GIF download failed")
        }
        if (connection.contentLengthLong > MAX_GIF_BYTES) {
            throw IllegalStateException("GIF is too large")
        }

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        connection.inputStream.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_GIF_BYTES) throw IllegalStateException("GIF is too large")
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        val signature = bytes.take(6).toByteArray().toString(Charsets.US_ASCII)
        require(signature == "GIF87a" || signature == "GIF89a") { "Catalog item is not a GIF" }

        val safeId = gif.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File.createTempFile("catalog_${safeId}_", ".gif", cacheDir).apply {
            writeBytes(bytes)
        }
    } finally {
        connection.disconnect()
    }
}

private fun isAllowedGifUrl(url: String): Boolean =
    (url.startsWith(NETLIFY_GIF_ASSET_PREFIX) || url.startsWith(LEGACY_GIF_ASSET_PREFIX)) &&
        url.endsWith(".gif", ignoreCase = true)
