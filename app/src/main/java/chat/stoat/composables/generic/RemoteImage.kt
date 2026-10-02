package chat.stoat.composables.generic

import android.util.DisplayMetrics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chat.stoat.internals.LocalMediaCache
import chat.stoat.logging.AppLogger
import com.bumptech.glide.integration.compose.CrossFade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable

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
    /** Pass true when the content_type is known to be image/gif — bypasses URL-based detection */
    forceAnimate: Boolean = false,
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

    // Any avatar, banner, background, or gif URL is always treated as animatable
    val isAnimatable = shouldAnimate && (
        forceAnimate ||
        url.contains("/avatars/", ignoreCase = true) ||
        url.contains("/banners/", ignoreCase = true) ||
        url.contains("/backgrounds/", ignoreCase = true) ||
        url.contains("/icons/", ignoreCase = true) ||
        url.contains(".gif", ignoreCase = true) ||
        url.contains("/gifs/", ignoreCase = true) ||
        url.contains("image/gif", ignoreCase = true) ||
        description?.endsWith(".gif", ignoreCase = true) == true ||
        (localFile != null && localFile.name.endsWith(".gif", ignoreCase = true))
    )

    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)

    // NEVER use CrossFade when animatable, because Glide wraps GifDrawable in TransitionDrawable,
    // which does not implement Animatable and causes the GIF to freeze on the first frame.
    val effectiveTransition = if (!isAnimatable && useTransition && overrideSize == 0 && localFile == null) CrossFade else null

    GlideImage(
        model = model,
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier
            .then(if (localFile == null) Modifier.background(placeholderColor) else Modifier)
            .then(if (width > 0) Modifier.width(pxAsDp(width)) else Modifier)
            .then(if (height > 0) Modifier.height(pxAsDp(height)) else Modifier),
        transition = effectiveTransition,
        requestBuilderTransform = { rb ->
            rb.listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    val modelStr = model?.toString() ?: ""
                    if (modelStr.contains("/backgrounds/") || modelStr.contains("/avatars/") || modelStr.contains(".gif", ignoreCase = true)) {
                        AppLogger.w("glide_image_load_failed", mapOf(
                            "url" to modelStr.take(150),
                            "error" to (e?.message ?: "unknown")
                        ))
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })

            if (isAnimatable) {
                // For animated GIFs: keep animation enabled, cache raw data, never thumbnail or downsample
                rb.diskCacheStrategy(DiskCacheStrategy.DATA)
                    .format(DecodeFormat.PREFER_ARGB_8888)
            } else {
                rb.diskCacheStrategy(DiskCacheStrategy.ALL)
                    .format(DecodeFormat.PREFER_ARGB_8888)
                    .downsample(DownsampleStrategy.FIT_CENTER)
                    .thumbnail(0.25f)
                if (ow > 0 && oh > 0) {
                    rb.override(ow, oh)
                }
                if (!shouldAnimate) {
                    rb.dontAnimate()
                } else rb
            }
        }
    )
}
