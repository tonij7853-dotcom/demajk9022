package chat.stoat.composables.screens.chat

import android.content.res.Configuration
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.api.routes.microservices.autumn.FileArgs
import chat.stoat.api.settings.UserInterfaceFont
import chat.stoat.composables.generic.RemoteImage
import chat.stoat.ui.theme.ClaudeTokens
import chat.stoat.ui.theme.StoatTheme
import chat.stoat.ui.theme.Theme
import kotlinx.coroutines.launch
import java.io.File

// Max file upload limit: 20 MB default
const val MAX_FILE_UPLOAD_BYTES = 20 * 1024 * 1024L

@Composable
fun FilePreviewSheet(
    args: FileArgs,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onToggleSpoiler: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var localIsSpoiler by remember { mutableStateOf(args.spoiler) }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (args.contentType.startsWith("image/") || args.contentType.startsWith("video/")) {
            RemoteImage(
                url = args.file.toURI().toURL().toString(),
                contentScale = ContentScale.Fit,
                description = null,
                allowAnimation = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(ClaudeTokens.Shapes.medium)
            )
        }
        Text(
            args.filename, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
        )
        Text(
            Formatter.formatFileSize(context, args.file.length()),
            color = LocalContentColor.current.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .clip(ClaudeTokens.Shapes.medium)
                .clickable {
                    onToggleSpoiler()
                    localIsSpoiler = !localIsSpoiler
                }
                .padding(top = 8.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.attachment_preview_spoiler))
                },
                supportingContent = {
                    Text(stringResource(R.string.attachment_preview_spoiler_description))
                },
                trailingContent = {
                    Switch(
                        checked = localIsSpoiler,
                        onCheckedChange = null,
                    )
                },
                colors = ListItemDefaults.colors().copy(
                    containerColor = Color.Transparent,
                )
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.attachment_preview_close))
            }
            if (canRemove) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_attach_file_off_24dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.attachment_preview_remove))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentManager(
    attachments: List<FileArgs>,
    uploading: Boolean,
    uploadProgress: Float = 0f,
    onRemove: (FileArgs) -> Unit,
    onToggleSpoiler: (FileArgs) -> Unit,
    modifier: Modifier = Modifier,
    canRemove: Boolean = true,
    canPreview: Boolean = true
) {
    var showPreviewSheet by remember { mutableStateOf(false) }
    var previewingAttachment by remember { mutableStateOf<FileArgs?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (showPreviewSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                showPreviewSheet = false
            },
            sheetState = sheetState
        ) {
            previewingAttachment?.let {
                FilePreviewSheet(
                    args = it,
                    canRemove = canRemove,
                    onRemove = {
                        onRemove(it)
                        scope.launch {
                            sheetState.hide()
                            showPreviewSheet = false
                        }
                    },
                    onToggleSpoiler = {
                        onToggleSpoiler(it)
                    },
                    onDismiss = {
                        scope.launch {
                            sheetState.hide()
                            showPreviewSheet = false
                        }
                    }
                )
            }
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = uploadProgress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "Upload progress"
    )

    // Check if any file exceeds maximum upload size
    val oversizeFile = attachments.firstOrNull { it.file.length() > MAX_FILE_UPLOAD_BYTES }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Warning banner for oversized files
        if (oversizeFile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(ClaudeTokens.Shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "File \"${oversizeFile.filename}\" exceeds the 20 MB limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Horizontal thumbnail & file strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            attachments.forEach { attachment ->
                val isImageOrGif = attachment.contentType.startsWith("image/") ||
                        attachment.filename.endsWith(".gif", ignoreCase = true)

                if (isImageOrGif) {
                    // Image/GIF thumbnail
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(ClaudeTokens.Shapes.attachmentThumbShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                width = ClaudeTokens.Borders.hairline,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = ClaudeTokens.Shapes.attachmentThumbShape
                            )
                            .clickable {
                                if (canPreview) {
                                    previewingAttachment = attachment
                                    showPreviewSheet = true
                                }
                            }
                    ) {
                        RemoteImage(
                            url = attachment.file.toURI().toURL().toString(),
                            contentScale = ContentScale.Crop,
                            description = attachment.filename,
                            allowAnimation = true,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Upload progress overlay
                        if (uploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Remove "x" button with touch-friendly target
                        if (canRemove && !uploading) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(ClaudeTokens.Shapes.pill)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { onRemove(attachment) }
                                    .semantics { contentDescription = "Remove ${attachment.filename}" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                } else {
                    // File document chip
                    Row(
                        modifier = Modifier
                            .clip(ClaudeTokens.Shapes.chipShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = ClaudeTokens.Borders.hairline,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = ClaudeTokens.Shapes.chipShape
                            )
                            .clickable {
                                if (canPreview) {
                                    previewingAttachment = attachment
                                    showPreviewSheet = true
                                }
                            }
                            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_file_present_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.widthIn(max = 140.dp)) {
                            Text(
                                text = attachment.filename,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = Formatter.formatShortFileSize(context, attachment.file.length()),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (canRemove && !uploading) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(ClaudeTokens.Shapes.pill)
                                    .clickable { onRemove(attachment) }
                                    .semantics { contentDescription = "Remove ${attachment.filename}" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = uploading) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clip(ClaudeTokens.Shapes.pill),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(name = "Attachment Strip Light", showBackground = true)
@Composable
fun AttachmentManagerLightPreview() {
    StoatTheme(requestedTheme = Theme.Light, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AttachmentManager(
                attachments = listOf(
                    FileArgs(filename = "screenshot.png", contentType = "image/png", file = File("screenshot.png")),
                    FileArgs(filename = "document.pdf", contentType = "application/pdf", file = File("document.pdf"))
                ),
                uploading = false,
                onRemove = {},
                onToggleSpoiler = {}
            )
        }
    }
}

@Preview(name = "Attachment Strip Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun AttachmentManagerDarkPreview() {
    StoatTheme(requestedTheme = Theme.Default, requestedUserInterfaceFont = UserInterfaceFont.Default) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            AttachmentManager(
                attachments = listOf(
                    FileArgs(filename = "screenshot.png", contentType = "image/png", file = File("screenshot.png")),
                    FileArgs(filename = "document.pdf", contentType = "application/pdf", file = File("document.pdf"))
                ),
                uploading = false,
                onRemove = {},
                onToggleSpoiler = {}
            )
        }
    }
}
