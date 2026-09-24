package chat.stoat.composables.generic

import android.util.DisplayMetrics
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

import androidx.compose.runtime.compositionLocalOf
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy

val LocalAllowGifAnimation = compositionLocalOf { true }

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun RemoteImage(
    url: String,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    width: Int = 0,
    height: Int = 0,
    allowAnimation: Boolean = true
) {
    val context = LocalContext.current
    val ambientAnimation = LocalAllowGifAnimation.current
    val shouldAnimate = allowAnimation && ambientAnimation

    fun pxAsDp(px: Int): Dp {
        return (
                px / (
                        context.resources
                            .displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
                        )
                ).dp
    }

    GlideImage(
        model = url,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier
            .then(if (width > 0) Modifier.width(pxAsDp(width)) else Modifier)
            .then(if (height > 0) Modifier.height(pxAsDp(height)) else Modifier),
        transition = CrossFade,
        requestBuilderTransform = { rb ->
            rb.diskCacheStrategy(DiskCacheStrategy.DATA)
                .downsample(DownsampleStrategy.AT_MOST)
                .format(DecodeFormat.PREFER_RGB_565)
            if (width > 0 && height > 0) {
                rb.override(width, height)
            }
            if (!shouldAnimate) rb.dontAnimate() else rb
        }
    )
}
