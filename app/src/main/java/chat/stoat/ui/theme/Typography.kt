package chat.stoat.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import chat.stoat.R
import chat.stoat.api.settings.UserInterfaceFont

val Inter = FontFamily(
    Font(R.font.inter_thin, FontWeight.Thin),
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
    Font(R.font.inter_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.inter_extralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.inter_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.inter_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.inter_black_italic, FontWeight.Black, FontStyle.Italic)
)
private val InterDisplay = FontFamily(
    Font(R.font.inter_display_thin, FontWeight.Thin),
    Font(R.font.inter_display_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_display_light, FontWeight.Light),
    Font(R.font.inter_display_regular, FontWeight.Normal),
    Font(R.font.inter_display_medium, FontWeight.Medium),
    Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_display_black, FontWeight.Black),
    Font(R.font.inter_display_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.inter_display_extralight_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.inter_display_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.inter_display_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_display_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.inter_display_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.inter_display_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.inter_display_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.inter_display_black_italic, FontWeight.Black, FontStyle.Italic)
)
val FragmentMono = FontFamily(
    Font(R.font.fragmentmono_regular, FontWeight.Normal),
    Font(R.font.fragmentmono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(DeviceFontFamilyName("monospace"), FontWeight.Normal, FontStyle.Normal),
    Font(DeviceFontFamilyName("monospace"), FontWeight.Normal, FontStyle.Italic)
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        R.font.googlesansflex,
        FontWeight.Thin,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(100),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.ExtraLight,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(200),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Light,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Normal,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Medium,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.SemiBold,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Bold,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.ExtraBold,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Black,
        FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.width(100f),
            FontVariation.slant(0f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Thin,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(100),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.ExtraLight,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(200),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Light,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(300),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Normal,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Medium,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.SemiBold,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Bold,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.ExtraBold,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    ),
    Font(
        R.font.googlesansflex,
        FontWeight.Black,
        FontStyle.Italic,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.width(100f),
            FontVariation.slant(-10f),
        )
    )
)

val Newsreader = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Medium),
    Font(R.font.newsreader, FontWeight.SemiBold),
    Font(R.font.newsreader, FontWeight.Bold),
    Font(R.font.newsreader, FontWeight.ExtraBold),
)

val StoatTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp
    ),
    displayMedium = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),

    headlineLarge = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    titleLarge = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Newsreader,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),

    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),

    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp // ~1.5 line height for comfortable message reading
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp // ~1.5 line height
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp // ~1.5 line height
    )
)

val GoogleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    )
)

fun getDefaultFont(): UserInterfaceFont {
    if (Build.MANUFACTURER == "Google" && Build.MODEL.startsWith("Pixel")) {
        return UserInterfaceFont.GoogleSansFlex
    }
    return UserInterfaceFont.Default
}