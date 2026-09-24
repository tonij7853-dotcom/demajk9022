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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.ui.theme.ClaudeTokens
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class GifItem(
    val id: String,
    val previewUrl: String,
    val mediaUrl: String,
    val title: String = ""
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
    var selectedCategory by remember { mutableStateOf("Trending") }
    var isDownloading by remember { mutableStateOf(false) }

    val categories = listOf("Trending", "Laugh", "Love", "Celebrate", "Thinking", "Cat", "Wave", "Yes", "No")

    // Curated high quality GIF list with reliable fallback items
    val curatedGifs = remember {
        listOf(
            GifItem("1", "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif", "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif", "Cat hello"),
            GifItem("2", "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif", "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif", "Celebrate"),
            GifItem("3", "https://media.giphy.com/media/l0HlBO7eyXzSZkJri/giphy.gif", "https://media.giphy.com/media/l0HlBO7eyXzSZkJri/giphy.gif", "Thumbs up"),
            GifItem("4", "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif", "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif", "Wave"),
            GifItem("5", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif", "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif", "Thinking"),
            GifItem("6", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "Nod yes"),
            GifItem("7", "https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif", "https://media.giphy.com/media/d2lcHJTG5Tscg/giphy.gif", "Sad cry"),
            GifItem("8", "https://media.giphy.com/media/5GoVLqeAOo6PK/giphy.gif", "https://media.giphy.com/media/5GoVLqeAOo6PK/giphy.gif", "Excited")
        )
    }

    val filteredGifs = remember(searchQuery, selectedCategory) {
        if (searchQuery.isNotBlank()) {
            curatedGifs.filter { it.title.contains(searchQuery, ignoreCase = true) }
        } else {
            curatedGifs
        }
    }

    fun downloadAndSelectGif(gif: GifItem) {
        if (isDownloading) return
        isDownloading = true
        scope.launch(Dispatchers.IO) {
            try {
                val client = HttpClient()
                val response = client.get(gif.mediaUrl)
                val bytes = response.readBytes()

                val tempFile = File(context.cacheDir, "gif_${System.currentTimeMillis()}.gif")
                FileOutputStream(tempFile).use { it.write(bytes) }

                val uri = Uri.fromFile(tempFile)
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    onGifSelected(uri)
                    onDismissRequest()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading = false
                }
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
                text = "Choose a GIF",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Search bar
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

            // Category chips
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
                        onClick = {
                            selectedCategory = category
                            searchQuery = if (category == "Trending") "" else category
                        },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isDownloading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    items(filteredGifs, key = { it.id }) { gif ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1.2f)
                                .clip(ClaudeTokens.Shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { downloadAndSelectGif(gif) }
                        ) {
                            RemoteImage(
                                url = gif.previewUrl,
                                description = gif.title,
                                contentScale = ContentScale.Crop,
                                allowAnimation = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
