package chat.stoat.composables.generic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chat.stoat.R
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.glide.ZoomableGlideImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * Full-resolution zoomable viewer for user avatars.
 * Allows users to inspect any profile picture at maximum original quality,
 * pinch to zoom, toggle between circular crop and full uncropped square image,
 * and share or copy the direct image link.
 */
@Composable
fun AvatarViewerDialog(
    avatarUrl: String,
    username: String,
    displayName: String? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var isCircleShape by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            // Dismiss on tapping the empty background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest
                    )
            )

            // Center image viewer with zoom support
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                val shape = if (isCircleShape) CircleShape else RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(Color(0xFF141518))
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.15f),
                            shape = shape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableGlideImage(
                        model = avatarUrl,
                        contentDescription = stringResource(id = R.string.avatar_alt, username),
                        state = rememberZoomableImageState(
                            rememberZoomableState(
                                zoomSpec = ZoomSpec(maxZoomFactor = 6f)
                            )
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            text = displayName ?: username,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "@$username",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Toggle between Circle and Square shape
                    IconButton(
                        onClick = { isCircleShape = !isCircleShape }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isCircleShape) R.drawable.ic_badge_24dp else R.drawable.ic_circle_24dp
                            ),
                            contentDescription = if (isCircleShape) "Show Square" else "Show Circle",
                            tint = Color.White
                        )
                    }

                    // Copy image link
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

                    // Share
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

            // Bottom helper text
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "Pinch to zoom • Full resolution",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
