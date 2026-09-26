package chat.stoat.composables.media

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.api.StoatHttp
import chat.stoat.media.GlobalAudioPlayer
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.launch

/**
 * Minimal Telegram-style inline audio player item in chat.
 * Playback is handled by [GlobalAudioPlayer] singleton so scrolling up/down never interrupts playback.
 */
@Composable
fun AudioPlayer(url: String, filename: String, contentType: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val showMenu = remember { mutableStateOf(false) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    val isCurrentTrack = GlobalAudioPlayer.currentUrl == url
    val isPlaying = isCurrentTrack && GlobalAudioPlayer.isPlaying
    val isLoading = isCurrentTrack && GlobalAudioPlayer.isLoading
    val currentPosition = if (isCurrentTrack) GlobalAudioPlayer.currentPosition else 0L
    val duration = if (isCurrentTrack) GlobalAudioPlayer.duration else 0L

    fun saveToStorage() {
        showMenu.value = false
        coroutineScope.launch {
            try {
                val resolver = context.applicationContext.contentResolver
                val uri = resolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Audio.Media.MIME_TYPE, contentType)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Dismod")
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                    }
                )
                uri?.let { destUri ->
                    resolver.openOutputStream(destUri)?.use { stream ->
                        val audioBytes = StoatHttp.get(url).readBytes()
                        stream.write(audioBytes)
                    }
                    resolver.update(
                        destUri,
                        ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        },
                        null,
                        null
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_viewer_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save audio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareUrl() {
        showMenu.value = false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        activityLauncher.launch(Intent.createChooser(intent, null))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Minimal Circular Play / Pause Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable {
                    GlobalAudioPlayer.play(context, url, filename)
                }
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (isPlaying) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Track Info & Progress
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = filename,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            if (isCurrentTrack && duration > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { GlobalAudioPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = GlobalAudioPlayer.formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = if (isCurrentTrack) "Playing..." else "Audio",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // More Actions (Save, Share)
        Box {
            IconButton(
                onClick = { showMenu.value = !showMenu.value },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_vert_24dp),
                    contentDescription = stringResource(R.string.media_viewer_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu.value,
                onDismissRequest = { showMenu.value = false }
            ) {
                DropdownMenuItem(
                    onClick = { saveToStorage() },
                    text = { Text(text = stringResource(R.string.media_viewer_save)) }
                )
                DropdownMenuItem(
                    onClick = { shareUrl() },
                    text = { Text(text = stringResource(R.string.media_viewer_share_url)) }
                )
            }
        }
    }
}

/**
 * Sticky Telegram-style top audio player bar pinned at the top of the chat view.
 * Displays title, play/pause controls, close button, and a thin progress bar.
 */
@Composable
fun TelegramTopAudioPlayerBar(
    modifier: Modifier = Modifier
) {
    val currentUrl = GlobalAudioPlayer.currentUrl
    val isVisible = currentUrl != null

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        // Play/Pause icon button
                        IconButton(
                            onClick = { GlobalAudioPlayer.togglePlay() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (GlobalAudioPlayer.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    painter = painterResource(
                                        if (GlobalAudioPlayer.isPlaying) R.drawable.ic_pause_24dp
                                        else R.drawable.ic_play_arrow_24dp
                                    ),
                                    contentDescription = if (GlobalAudioPlayer.isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Title & Time info
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = GlobalAudioPlayer.currentTitle ?: "Audio",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "${GlobalAudioPlayer.formatTime(GlobalAudioPlayer.currentPosition)} / ${GlobalAudioPlayer.formatTime(GlobalAudioPlayer.duration)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Close / Stop button
                        IconButton(
                            onClick = { GlobalAudioPlayer.stop() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close_24dp),
                                contentDescription = "Close player",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Progress bar neatly rounded at the bottom of the floating card
                    LinearProgressIndicator(
                        progress = { GlobalAudioPlayer.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
