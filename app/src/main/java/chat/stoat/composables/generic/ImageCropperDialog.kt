package chat.stoat.composables.generic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import chat.stoat.R
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

enum class CropShape {
    Circle,
    Square,
    Banner,
    WideBanner
}

/**
 * Discord-style interactive Crop and Resize dialog for Profile Pictures and Banners.
 * Supports:
 * - Free multi-touch pinch to zoom and pan.
 * - 3x3 Rule-of-Thirds grid overlay.
 * - Smooth slider / ruler for fine zoom control with % display.
 * - 90° rotation.
 * - Fit / Fill / Aspect ratio toggle.
 * - Generates high-fidelity cropped image.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    cropShape: CropShape = CropShape.Square,
    onDismissRequest: () -> Unit,
    onCropSuccess: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessingCrop by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var minScale by remember { mutableFloatStateOf(1f) }
    var maxScale by remember { mutableFloatStateOf(5f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var imageDisplaySize by remember { mutableStateOf(IntSize.Zero) }

    val targetAspectRatio = when (cropShape) {
        CropShape.Circle, CropShape.Square -> 1.0f
        CropShape.Banner -> 16f / 9f
        CropShape.WideBanner -> 2.5f
    }

    var isGif by remember { mutableStateOf(false) }

    // Load and orient bitmap on launch
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                // Check if animated GIF
                val mime = context.contentResolver.getType(imageUri)
                val isMimeGif = mime?.equals("image/gif", ignoreCase = true) == true
                val isExtGif = imageUri.path?.lowercase()?.endsWith(".gif") == true ||
                        imageUri.lastPathSegment?.lowercase()?.endsWith(".gif") == true

                var isHeaderGif = false
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val header = ByteArray(6)
                    val count = stream.read(header)
                    isHeaderGif = count >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                            header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
                }

                isGif = isMimeGif || isExtGif || isHeaderGif

                if (!isGif) {
                    // Only decode bitmap for non-GIFs (GIFs show live in GlideImage)
                    val input: InputStream? = context.contentResolver.openInputStream(imageUri)
                    val raw = BitmapFactory.decodeStream(input)
                    input?.close()

                    if (raw != null) {
                        val exifInput: InputStream? = context.contentResolver.openInputStream(imageUri)
                        val exif = exifInput?.let { ExifInterface(it) }
                        val orientation = exif?.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        ) ?: ExifInterface.ORIENTATION_NORMAL
                        exifInput?.close()

                        val oriented = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> {
                                val m = Matrix().apply { postRotate(90f) }
                                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                            }
                            ExifInterface.ORIENTATION_ROTATE_180 -> {
                                val m = Matrix().apply { postRotate(180f) }
                                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                            }
                            ExifInterface.ORIENTATION_ROTATE_270 -> {
                                val m = Matrix().apply { postRotate(270f) }
                                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                            }
                            else -> raw
                        }
                        sourceBitmap = oriented
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. TOP APP BAR (X - Edit Image - ✓)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        enabled = !isProcessingCrop
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_24dp),
                            contentDescription = "Cancel",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = when (cropShape) {
                            CropShape.Circle, CropShape.Square -> if (isGif) "Profile GIF (Animated)" else "Edit Profile Picture"
                            CropShape.Banner, CropShape.WideBanner -> if (isGif) "Banner GIF (Animated)" else "Edit Banner"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    IconButton(
                        onClick = {
                            if (isGif) {
                                // Preserve full animated GIF playback
                                onCropSuccess(imageUri)
                                return@IconButton
                            }

                            val bmp = sourceBitmap ?: return@IconButton
                            if (isProcessingCrop) return@IconButton
                            isProcessingCrop = true

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val croppedUri = performCrop(
                                        context = context,
                                        source = bmp,
                                        scale = scale,
                                        offset = offset,
                                        rotation = rotationDegrees,
                                        viewportSize = viewportSize,
                                        targetAspect = targetAspectRatio,
                                        cropShape = cropShape
                                    )
                                    withContext(Dispatchers.Main) {
                                        isProcessingCrop = false
                                        onCropSuccess(croppedUri)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isProcessingCrop = false
                                    }
                                }
                            }
                        },
                        enabled = !isProcessingCrop && (sourceBitmap != null || isGif)
                    ) {
                        if (isProcessingCrop) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_24dp),
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // 2. MAIN CROP CANVAS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Show loading spinner only for non-GIFs while bitmap decodes
                    if (isLoading && !isGif) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (isGif || sourceBitmap != null) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val maxW = constraints.maxWidth.toFloat()
                            val maxH = constraints.maxHeight.toFloat()

                            // Compute viewport dimensions matching target aspect ratio
                            val vpWidth: Float
                            val vpHeight: Float
                            if (maxW / maxH > targetAspectRatio) {
                                vpHeight = maxH * 0.85f
                                vpWidth = vpHeight * targetAspectRatio
                            } else {
                                vpWidth = maxW * 0.95f
                                vpHeight = vpWidth / targetAspectRatio
                            }

                            val density = LocalDensity.current
                            val vpWidthDp = with(density) { vpWidth.toDp() }
                            val vpHeightDp = with(density) { vpHeight.toDp() }

                            // Viewport Box with gesture detection
                            Box(
                                modifier = Modifier
                                    .size(vpWidthDp, vpHeightDp)
                                    .onGloballyPositioned {
                                        viewportSize = it.size
                                    }
                                    .clipToBounds()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(minScale, maxScale)
                                            offset = Offset(
                                                x = offset.x + pan.x,
                                                y = offset.y + pan.y
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Image with transforms — animated GIF uses URI, static uses bitmap
                                GlideImage(
                                    model = if (isGif) imageUri else sourceBitmap!!,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onGloballyPositioned {
                                            imageDisplaySize = it.size
                                        }
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                            rotationZ = rotationDegrees.toFloat()
                                        }
                                )

                                // 3x3 Rule of Thirds Grid Overlay
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height
                                    val gridColor = Color.White.copy(alpha = 0.45f)
                                    val strokeW = 1.dp.toPx()

                                    // Outer border
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = Stroke(width = 1.5.dp.toPx())
                                    )

                                    // Vertical grid lines
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(w / 3f, 0f),
                                        end = Offset(w / 3f, h),
                                        strokeWidth = strokeW
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(2f * w / 3f, 0f),
                                        end = Offset(2f * w / 3f, h),
                                        strokeWidth = strokeW
                                    )

                                    // Horizontal grid lines
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, h / 3f),
                                        end = Offset(w, h / 3f),
                                        strokeWidth = strokeW
                                    )
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, 2f * h / 3f),
                                        end = Offset(w, 2f * h / 3f),
                                        strokeWidth = strokeW
                                    )

                                    // If Circle mode, draw subtle circular guide
                                    if (cropShape == CropShape.Circle) {
                                        drawCircle(
                                            color = Color.White.copy(alpha = 0.35f),
                                            radius = minOf(w, h) / 2f,
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. BOTTOM CONTROLS (Scale slider + Ruler + Rotate / Reset)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1015))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zoom percentage label
                    val percent = ((scale / minScale) * 100f).roundToInt()
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Zoom Slider & Visual Ruler
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = minScale..maxScale,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.7f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons (Rotate 90°, Reset / Fit)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_crop_rotate_24dp),
                                    contentDescription = "Rotate",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Rotate",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        // Scale / Reset Fit button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                    rotationDegrees = 0
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_crop_free_24dp),
                                    contentDescription = "Fit",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "Scale",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Performs crop and rotation transforms on background thread, producing a clean,
 * high-resolution cropped image file.
 */
private suspend fun performCrop(
    context: Context,
    source: Bitmap,
    scale: Float,
    offset: Offset,
    rotation: Int,
    viewportSize: IntSize,
    targetAspect: Float,
    cropShape: CropShape
): Uri = withContext(Dispatchers.IO) {
    // 1. Apply user rotation if any
    val rotated = if (rotation % 360 != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } else {
        source
    }

    val srcW = rotated.width.toFloat()
    val srcH = rotated.height.toFloat()

    val vpW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1080f
    val vpH = if (viewportSize.height > 0) viewportSize.height.toFloat() else (1080f / targetAspect)

    // Base fitting scale (ContentScale.Fit inside viewport)
    val baseScale = minOf(vpW / srcW, vpH / srcH)
    val totalScale = baseScale * scale

    val displayedW = srcW * totalScale
    val displayedH = srcH * totalScale

    // Viewport center in image coordinates
    val imgCenterX = (displayedW / 2f) + offset.x
    val imgCenterY = (displayedH / 2f) + offset.y

    // Viewport bounds mapped to image space
    val cropLeftDisp = (displayedW / 2f) - (vpW / 2f) - offset.x
    val cropTopDisp = (displayedH / 2f) - (vpH / 2f) - offset.y

    val cropLeftSrc = (cropLeftDisp / totalScale).coerceIn(0f, srcW - 1f)
    val cropTopSrc = (cropTopDisp / totalScale).coerceIn(0f, srcH - 1f)

    val cropWidthSrc = (vpW / totalScale).coerceIn(1f, srcW - cropLeftSrc)
    val cropHeightSrc = (vpH / totalScale).coerceIn(1f, srcH - cropTopSrc)

    // Crop sub-bitmap
    val cropped = Bitmap.createBitmap(
        rotated,
        cropLeftSrc.toInt(),
        cropTopSrc.toInt(),
        cropWidthSrc.toInt(),
        cropHeightSrc.toInt()
    )

    // Save to cache file
    val filename = "crop_${System.currentTimeMillis()}.png"
    val cacheFile = File(context.cacheDir, filename)
    cacheFile.outputStream().use { out ->
        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }

    cacheFile.toUri()
}
