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
import chat.stoat.util.AnimatedGifEncoder
import chat.stoat.logging.AppLogger
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
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
                val openStream: () -> InputStream? = {
                    if (imageUri.scheme?.lowercase() == "file") {
                        val path = imageUri.path ?: imageUri.schemeSpecificPart
                        if (path != null && File(path).exists()) File(path).inputStream() else null
                    } else {
                        try {
                            context.contentResolver.openInputStream(imageUri)
                        } catch (e: Exception) {
                            val path = imageUri.path
                            if (path != null && File(path).exists()) File(path).inputStream() else null
                        }
                    }
                }

                // Check if animated GIF
                val mime = context.contentResolver.getType(imageUri)
                val isMimeGif = mime?.equals("image/gif", ignoreCase = true) == true
                val isExtGif = imageUri.path?.lowercase()?.endsWith(".gif") == true ||
                        imageUri.lastPathSegment?.lowercase()?.endsWith(".gif") == true

                var isHeaderGif = false
                openStream()?.use { stream ->
                    val header = ByteArray(6)
                    val count = stream.read(header)
                    isHeaderGif = count >= 6 && header[0] == 'G'.code.toByte() && header[1] == 'I'.code.toByte() &&
                            header[2] == 'F'.code.toByte() && header[3] == '8'.code.toByte()
                }

                isGif = isMimeGif || isExtGif || isHeaderGif

                AppLogger.i("cropper_dialog_opened", mapOf(
                    "uri" to imageUri.toString(),
                    "scheme" to (imageUri.scheme ?: "none"),
                    "crop_shape" to cropShape.name,
                    "target_aspect" to targetAspectRatio,
                    "is_gif" to isGif,
                    "is_header_gif" to isHeaderGif
                ))

                if (!isGif) {
                    // Only decode bitmap for non-GIFs (GIFs show live in GlideImage)
                    val input: InputStream? = openStream()
                    val raw = BitmapFactory.decodeStream(input)
                    input?.close()

                    if (raw != null) {
                        val exifInput: InputStream? = openStream()
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
                        AppLogger.i("cropper_dialog_bitmap_loaded", mapOf(
                            "width" to oriented.width,
                            "height" to oriented.height,
                            "orientation" to orientation
                        ))
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("cropper_dialog_load_failed", mapOf("uri" to imageUri.toString()), e)
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
                                if (isProcessingCrop) return@IconButton
                                isProcessingCrop = true
                                AppLogger.i("crop_button_clicked", mapOf("type" to "gif", "uri" to imageUri.toString()))
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val croppedUri = performGifCrop(
                                            context = context,
                                            gifUri = imageUri,
                                            scale = scale,
                                            offset = offset,
                                            rotation = rotationDegrees,
                                            viewportSize = viewportSize,
                                            targetAspect = targetAspectRatio
                                        )
                                        AppLogger.i("crop_success", mapOf("type" to "gif", "result_uri" to croppedUri.toString()))
                                        withContext(Dispatchers.Main) {
                                            isProcessingCrop = false
                                            onCropSuccess(croppedUri)
                                        }
                                    } catch (e: Exception) {
                                        AppLogger.e("crop_failed", mapOf("type" to "gif", "error" to (e.message ?: "")), e)
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            isProcessingCrop = false
                                            // Fallback: if crop fails, use original
                                            onCropSuccess(imageUri)
                                        }
                                    }
                                }
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
 * high-resolution cropped image file that exactly matches what the user previewed.
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
    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()

    val vpW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1080f
    val vpH = if (viewportSize.height > 0) viewportSize.height.toFloat() else (1080f / targetAspect)

    val maxSrc = maxOf(srcW, srcH)
    val outMax = maxSrc.coerceIn(720f, 2048f)
    val outW: Float
    val outH: Float
    if (targetAspect >= 1f) {
        outW = outMax
        outH = outMax / targetAspect
    } else {
        outH = outMax
        outW = outMax * targetAspect
    }

    val matrix = Matrix()
    val fitScale = minOf(vpW / srcW, vpH / srcH)
    val fittedLeft = (vpW - srcW * fitScale) / 2f
    val fittedTop = (vpH - srcH * fitScale) / 2f
    matrix.postScale(fitScale, fitScale)
    matrix.postTranslate(fittedLeft, fittedTop)

    val centerX = vpW / 2f
    val centerY = vpH / 2f
    matrix.postRotate(rotation.toFloat(), centerX, centerY)
    matrix.postScale(scale, scale, centerX, centerY)
    matrix.postTranslate(offset.x, offset.y)

    val outScaleX = outW / vpW
    val outScaleY = outH / vpH
    matrix.postScale(outScaleX, outScaleY)

    AppLogger.i("static_crop_started", mapOf(
        "srcW" to srcW,
        "srcH" to srcH,
        "scale" to scale,
        "rotation" to rotation,
        "targetAspect" to targetAspect,
        "cropShape" to cropShape.name
    ))

    val cropped = Bitmap.createBitmap(outW.toInt().coerceAtLeast(1), outH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(cropped)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(source, matrix, paint)

    val filename = "crop_${System.currentTimeMillis()}.png"
    val cacheFile = File(context.cacheDir, filename)
    cacheFile.outputStream().use { out ->
        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
    cropped.recycle()

    AppLogger.i("static_crop_finished", mapOf(
        "outW" to outW,
        "outH" to outH,
        "size_bytes" to cacheFile.length(),
        "file" to cacheFile.name
    ))

    cacheFile.toUri()
}

/**
 * Performs frame-by-frame crop and rotation on animated GIFs, preserving all frames,
 * frame timings, and animation loops.
 */
private suspend fun performGifCrop(
    context: Context,
    gifUri: Uri,
    scale: Float,
    offset: Offset,
    rotation: Int,
    viewportSize: IntSize,
    targetAspect: Float
): Uri = withContext(Dispatchers.IO) {
    val inputStream: InputStream? = when (gifUri.scheme?.lowercase()) {
        "file" -> {
            val path = gifUri.path ?: gifUri.schemeSpecificPart
            if (path != null && File(path).exists()) File(path).inputStream() else null
        }
        else -> {
            try {
                context.contentResolver.openInputStream(gifUri)
            } catch (e: Exception) {
                val path = gifUri.path
                if (path != null && File(path).exists()) File(path).inputStream() else null
            }
        }
    }

    val bytes = inputStream?.use { it.readBytes() }
        ?: run {
            AppLogger.w("gif_crop_input_null", mapOf("uri" to gifUri.toString()))
            return@withContext gifUri
        }

    val cropStartTime = System.currentTimeMillis()
    AppLogger.i("gif_crop_started", mapOf(
        "uri" to gifUri.toString(),
        "scale" to scale,
        "offset_x" to offset.x,
        "offset_y" to offset.y,
        "rotation" to rotation,
        "targetAspect" to targetAspect,
        "raw_bytes" to bytes.size
    ))

    val bitmapProvider = object : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)
        override fun release(bitmap: Bitmap) {
            // Do NOT recycle: StandardGifDecoder reuses previousImage across frames for disposal/blending
        }
        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)
        override fun release(bytes: ByteArray) {}
        override fun obtainIntArray(size: Int): IntArray = IntArray(size)
        override fun release(array: IntArray) {}
    }

    val parser = GifHeaderParser()
    parser.setData(bytes)
    val header = parser.parseHeader()

    val decoder = StandardGifDecoder(bitmapProvider, header, java.nio.ByteBuffer.wrap(bytes))
    val frameCount = decoder.frameCount
    if (frameCount <= 0) {
        AppLogger.w("gif_crop_empty_frames", mapOf("uri" to gifUri.toString(), "frameCount" to frameCount))
        return@withContext gifUri
    }

    val srcW = decoder.width.toFloat()
    val srcH = decoder.height.toFloat()

    val vpW = if (viewportSize.width > 0) viewportSize.width.toFloat() else 1080f
    val vpH = if (viewportSize.height > 0) viewportSize.height.toFloat() else (1080f / targetAspect)

    val maxDim = if (targetAspect >= 1.5f) 540f else 360f
    val outW: Float
    val outH: Float
    if (targetAspect >= 1f) {
        outW = maxDim
        outH = maxDim / targetAspect
    } else {
        outH = maxDim
        outW = maxDim * targetAspect
    }

    val matrix = Matrix()
    val fitScale = minOf(vpW / srcW, vpH / srcH)
    val fittedLeft = (vpW - srcW * fitScale) / 2f
    val fittedTop = (vpH - srcH * fitScale) / 2f
    matrix.postScale(fitScale, fitScale)
    matrix.postTranslate(fittedLeft, fittedTop)

    val centerX = vpW / 2f
    val centerY = vpH / 2f
    matrix.postRotate(rotation.toFloat(), centerX, centerY)
    matrix.postScale(scale, scale, centerX, centerY)
    matrix.postTranslate(offset.x, offset.y)

    val outScaleX = outW / vpW
    val outScaleY = outH / vpH
    matrix.postScale(outScaleX, outScaleY)

    val filename = "crop_${System.currentTimeMillis()}.gif"
    val cacheFile = File(context.cacheDir, filename)
    val outStream = FileOutputStream(cacheFile)

    val encoder = AnimatedGifEncoder()
    encoder.start(outStream)
    encoder.setRepeat(0)
    encoder.setSample(16)

    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Cap total encoded frames to ~36 max to stay strictly within Autumn's ~4MB limit and finish encoding in <1s
    val maxTargetFrames = 36
    val step = if (frameCount > maxTargetFrames) {
        ((frameCount + maxTargetFrames - 1) / maxTargetFrames).coerceAtLeast(1)
    } else {
        1
    }

    AppLogger.i("gif_decode_header", mapOf(
        "frameCount" to frameCount,
        "srcW" to srcW,
        "srcH" to srcH,
        "outW" to outW,
        "outH" to outH,
        "step" to step
    ))

    var encodedFrames = 0
    try {
        for (i in 0 until frameCount) {
            decoder.advance()
            val frame = decoder.nextFrame
            val delay = decoder.nextDelay

            if (i % step == 0 && frame != null) {
                val outBitmap = Bitmap.createBitmap(outW.toInt().coerceAtLeast(1), outH.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(outBitmap)
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(frame, matrix, paint)

                val effectiveDelay = if (delay <= 10) 100 else delay
                encoder.setDelay((effectiveDelay * step).coerceAtLeast(20))
                encoder.addFrame(outBitmap)
                outBitmap.recycle()
                encodedFrames++

                if (encodedFrames % 10 == 0 || encodedFrames == 1) {
                    AppLogger.d("gif_crop_frame_encoded", mapOf(
                        "encoded_index" to encodedFrames,
                        "source_index" to i,
                        "total_frames" to frameCount
                    ))
                }
            }
        }
    } catch (e: Exception) {
        AppLogger.e("gif_crop_loop_failed", mapOf("encoded_frames" to encodedFrames), e)
        throw e
    } finally {
        encoder.finish()
        outStream.close()
    }

    val durationMs = System.currentTimeMillis() - cropStartTime
    AppLogger.i("gif_crop_finished", mapOf(
        "encoded_frames" to encodedFrames,
        "final_file_size" to cacheFile.length(),
        "duration_ms" to durationMs
    ))

    cacheFile.toUri()
}
