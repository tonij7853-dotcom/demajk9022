package chat.stoat.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system tokens inspired by Claude's calm, clean, spacious, and readable aesthetic.
 * All spacing, radius, motion, and color helpers are centralized here.
 */
object ClaudeTokens {
    // Spacing scale: 8dp grid with generous padding
    object Spacing {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
        val xxxl: Dp = 48.dp

        // Specific component paddings
        val messageRowHorizontal: Dp = 16.dp
        val messageRowVertical: Dp = 6.dp
        val messageGroupVertical: Dp = 2.dp
        val composerPaddingHorizontal: Dp = 16.dp
        val composerPaddingVertical: Dp = 10.dp
        val screenPadding: Dp = 16.dp
    }

    // Corner Radii: 12-16dp
    object Shapes {
        val radiusSmall: Dp = 8.dp
        val radiusMedium: Dp = 12.dp
        val radiusLarge: Dp = 16.dp
        val radiusExtraLarge: Dp = 24.dp
        val radiusPill: Dp = 999.dp

        val small = RoundedCornerShape(radiusSmall)
        val medium = RoundedCornerShape(radiusMedium)
        val large = RoundedCornerShape(radiusLarge)
        val extraLarge = RoundedCornerShape(radiusExtraLarge)
        val pill = RoundedCornerShape(radiusPill)

        // Specific shapes
        val composerShape = RoundedCornerShape(20.dp)
        val attachmentThumbShape = RoundedCornerShape(12.dp)
        val chipShape = RoundedCornerShape(10.dp)
        val messageBubbleShape = RoundedCornerShape(16.dp)
    }

    // Borders & Hairlines
    object Borders {
        val hairline: Dp = 1.dp
        val thin: Dp = 1.5.dp
        val focus: Dp = 2.dp
    }

    // Motion: subtle and short (150-250ms)
    object Motion {
        const val DurationShortMs: Int = 150
        const val DurationMediumMs: Int = 200
        const val DurationStandardMs: Int = 250

        val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val DecelerateEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
        val AccelerateEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

        fun <T> subtleSpec(durationMillis: Int = DurationMediumMs): FiniteAnimationSpec<T> =
            tween(durationMillis = durationMillis, easing = StandardEasing)
    }

    // Colors: terracotta accents
    object Accent {
        val TerracottaLight = Color(0xFFC6613F)
        val TerracottaDark = Color(0xFFD97757)
        val TerracottaHover = Color(0xFFB55434)
        val TerracottaDisabled = Color(0x66C6613F)

        @Composable
        fun terracotta(isDark: Boolean): Color =
            if (isDark) TerracottaDark else TerracottaLight
    }
}
