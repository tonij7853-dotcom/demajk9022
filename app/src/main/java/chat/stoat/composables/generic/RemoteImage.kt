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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import chat.stoat.internals.LocalMediaCache
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

import androidx.compose.runtime.compositionLocalOf
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifOptions
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
    overrideSize: Int = 0,
    allowAnimation: Boolean = true,
    useTransition: Boolean = true
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

    val ow = if (overrideSize > 0) overrideSize else width
    val oh = if (overrideSize > 0) overrideSize else height

    val localFile = remember(url) { LocalMediaCache.getFile(url) }
    val model: Any = localFile ?: url

    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)

    GlideImage(
        model = model,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier
            .then(if (localFile == null) Modifier.background(placeholderColor) else Modifier)
            .then(if (width > 0) Modifier.width(pxAsDp(width)) else Modifier)
            .then(if (height > 0) Modifier.height(pxAsDp(height)) else Modifier),
        transition = if (useTransition && overrideSize == 0 && localFile == null) CrossFade else null,
        requestBuilderTransform = { rb ->
            rb.diskCacheStrategy(DiskCacheStrategy.ALL)
                .downsample(DownsampleStrategy.FIT_CENTER)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .set(GifOptions.DISABLE_ANIMATION, !shouldAnimate)
                .thumbnail(0.25f)
            if (ow > 0 && oh > 0) {
                rb.override(ow, oh)
            }
            if (!shouldAnimate) rb.dontAnimate() else rb
        }
    )
}
