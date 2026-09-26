package chat.stoat.composables.generic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chat.stoat.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.glide.ZoomableGlideImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Full-screen Discord-style avatar previewer.
 * Renders the user's profile picture in full resolution edge-to-edge,
 * preserving natural aspect ratio without artificial card framing or downscaling.
 * Supports smooth pinch-to-zoom, pan, double-tap zoom, saving to gallery, and sharing.
 */
@Composable
fun AvatarViewerDialog(
    avatarUrl: String,
    username: String,
    displayName: String? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showControls by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    val resolvedUrl = remember(avatarUrl) {
        when {
            (avatarUrl.contains("/avatars/") || avatarUrl.contains("/backgrounds/")) &&
                    !avatarUrl.endsWith("/original") && !avatarUrl.contains("?") -> {
                "$avatarUrl/original"
            }
            else -> avatarUrl
        }
    }

    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 10f)
    )
    val zoomableImageState = rememberZoomableImageState(zoomableState)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Full-screen zoomable avatar image (natural aspect ratio, edge-to-edge, crystal-clear full resolution)
            ZoomableGlideImage(
                model = resolvedUrl,
                contentDescription = stringResource(id = R.string.avatar_alt, username),
                state = zoomableImageState,
                modifier = Modifier.fillMaxSize(),
                onClick = {
                    showControls = !showControls
                }
            )

            // Minimal round loading indicator while preview loads
            AnimatedVisibility(
                visible = !zoomableImageState.isImageDisplayed,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    strokeWidth = 3.dp
                )
            }

            // Discord-style top overlay action bar with gradient scrim
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismissRequest) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close_24dp),
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Column {
                                Text(
                                    text = displayName?.takeIf { it.isNotBlank() } ?: username,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@$username",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Save / Download to gallery
                            IconButton(
                                onClick = {
                                    if (isSaving) return@IconButton
                                    isSaving = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val client = OkHttpClient()
                                            val targetUrl = resolvedUrl
                                            var response = try {
                                                val req = Request.Builder().url(targetUrl).build()
                                                val res = client.newCall(req).execute()
                                                if (res.isSuccessful) res else null
                                            } catch (e: Exception) {
                                                null
                                            }
                                            if (response == null && targetUrl != avatarUrl) {
                                                val fallbackReq = Request.Builder().url(avatarUrl).build()
                                                response = client.newCall(fallbackReq).execute()
                                            }
                                            if (response == null || !response.isSuccessful) {
                                                throw Exception("HTTP ${response?.code ?: "failed"}")
                                            }
                                            val bytes = response.body.bytes()
                                                ?: throw Exception("Empty image")
                                            val contentType = response.header("Content-Type") ?: ""
                                            val isGif = contentType.contains("gif", ignoreCase = true) ||
                                                    avatarUrl.contains(".gif", ignoreCase = true)
                                            val ext = if (isGif) "gif" else "png"
                                            val mime = if (isGif) "image/gif" else "image/png"
                                            val filename = "dismod_${username}_${System.currentTimeMillis()}.$ext"

                                            val cv = ContentValues().apply {
                                                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                                put(MediaStore.Images.Media.MIME_TYPE, mime)
                                                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dismod")
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                                }
                                            }

                                            val resolver = context.contentResolver
                                            val uri = resolver.insert(
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                cv
                                            ) ?: throw Exception("Failed to insert media")

                                            resolver.openOutputStream(uri)?.use { os ->
                                                os.write(bytes)
                                                os.flush()
                                            }

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                cv.clear()
                                                cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                                                resolver.update(uri, cv, null, null)
                                            }

                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                Toast.makeText(
                                                    context,
                                                    "Saved to gallery",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                Toast.makeText(
                                                    context,
                                                    "Save failed: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_download_24dp),
                                        contentDescription = "Save to gallery",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Copy direct image link
                            IconButton(
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Avatar URL", avatarUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied avatar link", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_content_copy_24dp),
                                    contentDescription = "Copy link",
                                    tint = Color.White
                                )
                            }

                            // Share avatar link
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, avatarUrl)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Avatar"))
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_ios_share_24dp),
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
