package chat.stoat.composables.generic

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import chat.stoat.R
import chat.stoat.api.settings.LoadedSettings
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@Composable
fun InlineMediaPicker(
    currentModel: Any?,
    modifier: Modifier = Modifier,
    mimeType: String = "image/*",
    circular: Boolean = false,
    useAvatarCircularity: Boolean = false,
    enableCrop: Boolean = true,
    cropShape: CropShape = if (circular) CropShape.Circle else CropShape.WideBanner,
    onPick: (Uri) -> Unit,
    canRemove: Boolean = true,
    onRemove: () -> Unit = {},
    enabled: Boolean = true
) {
    if (circular) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            InlineMediaPickerMediaPicker(
                currentModel = currentModel,
                mimeType = mimeType,
                circular = true,
                useAvatarCircularity = useAvatarCircularity,
                enableCrop = enableCrop,
                cropShape = cropShape,
                onPick = onPick
            )

            if (canRemove) {
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        onRemove()
                    },
                    enabled = (currentModel != null) && enabled
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24dp),
                        contentDescription = stringResource(R.string.inline_media_picker_remove)
                    )
                }
            }
        }
    } else {
        Column(modifier.fillMaxWidth()) {
            InlineMediaPickerMediaPicker(
                currentModel = currentModel,
                mimeType = mimeType,
                circular = false,
                enableCrop = enableCrop,
                cropShape = cropShape,
                onPick = onPick
            )

            if (canRemove) {
                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        onRemove()
                    },
                    enabled = (currentModel != null) && enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24dp),
                        contentDescription = null
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.inline_media_picker_remove),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun isGifUri(context: android.content.Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)
    if (mime?.equals("image/gif", ignoreCase = true) == true) return true
    val path = uri.path?.lowercase() ?: ""
    if (path.endsWith(".gif")) return true
    val lastSegment = uri.lastPathSegment?.lowercase() ?: ""
    if (lastSegment.endsWith(".gif")) return true

    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(6)
            val count = stream.read(header)
            count >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
        } ?: false
    } catch (e: Exception) {
        false
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun InlineMediaPickerMediaPicker(
    currentModel: Any?,
    mimeType: String = "image/*",
    circular: Boolean = false,
    useAvatarCircularity: Boolean = false,
    enableCrop: Boolean = true,
    cropShape: CropShape = if (circular) CropShape.Circle else CropShape.WideBanner,
    enabled: Boolean = true,
    onPick: (Uri) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val documentsUiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (enableCrop) {
                pendingCropUri = uri
            } else {
                onPick(uri)
            }
        }
    }

    if (pendingCropUri != null) {
        ImageCropperDialog(
            imageUri = pendingCropUri!!,
            cropShape = cropShape,
            onDismissRequest = { pendingCropUri = null },
            onCropSuccess = { cropped ->
                pendingCropUri = null
                onPick(cropped)
            }
        )
    }

    if (currentModel != null) {
        GlideImage(
            model = currentModel,
            contentDescription = stringResource(R.string.inline_media_picker_current_description),
            contentScale = ContentScale.Crop,
            requestBuilderTransform = { rb ->
                rb.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
            },
            modifier = if (circular) {
                Modifier
                    .then(
                        if (useAvatarCircularity) {
                            Modifier.clip(RoundedCornerShape(LoadedSettings.avatarRadius))
                        } else {
                            Modifier.clip(CircleShape)
                        }
                    )
                    .width(96.dp)
                    .height(96.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .fillMaxWidth()
                    .height(160.dp)
            }.clickable {
                if (enabled) documentsUiLauncher.launch(mimeType)
            }
        )
    } else {
        Box(
            modifier = if (circular) {
                Modifier
                    .then(
                        if (useAvatarCircularity) {
                            Modifier.clip(RoundedCornerShape(LoadedSettings.avatarRadius))
                        } else {
                            Modifier.clip(CircleShape)
                        }
                    )
                    .width(96.dp)
                    .height(96.dp)
            } else {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .fillMaxWidth()
                    .height(160.dp)
            }
                .clickable {
                    if (enabled) documentsUiLauncher.launch(mimeType)
                }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (circular) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.inline_media_picker_no_media_placeholder)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo_library_24dp),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.inline_media_picker_no_media_placeholder),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}