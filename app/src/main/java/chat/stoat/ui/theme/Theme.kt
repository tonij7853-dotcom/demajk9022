package chat.stoat.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import chat.stoat.api.settings.UserInterfaceFont
import chat.stoat.core.model.data.OverridableColourScheme

val LightColorScheme = lightColorScheme(
    primary = Colour.PrimaryLight,
    onPrimary = Colour.OnPrimaryLight,
    primaryContainer = Colour.PrimaryContainerLight,
    onPrimaryContainer = Colour.OnPrimaryContainerLight,
    inversePrimary = Colour.InversePrimaryLight,
    secondary = Colour.SecondaryLight,
    onSecondary = Colour.OnSecondaryLight,
    secondaryContainer = Colour.SecondaryContainerLight,
    onSecondaryContainer = Colour.OnSecondaryContainerLight,
    tertiary = Colour.TertiaryLight,
    onTertiary = Colour.OnTertiaryLight,
    tertiaryContainer = Colour.TertiaryContainerLight,
    onTertiaryContainer = Colour.OnTertiaryContainerLight,
    background = Colour.BackgroundLight,
    onBackground = Colour.OnBackgroundLight,
    surface = Colour.SurfaceLight,
    onSurface = Colour.OnSurfaceLight,
    surfaceVariant = Colour.SurfaceVariantLight,
    onSurfaceVariant = Colour.OnSurfaceVariantLight,
    surfaceTint = Colour.SurfaceTintLight,
    inverseSurface = Colour.InverseSurfaceLight,
    inverseOnSurface = Colour.InverseOnSurfaceLight,
    error = Colour.ErrorLight,
    onError = Colour.OnErrorLight,
    errorContainer = Colour.ErrorContainerLight,
    onErrorContainer = Colour.OnErrorContainerLight,
    outline = Colour.OutlineLight,
    outlineVariant = Colour.OutlineVariantLight,
    scrim = Colour.ScrimLight,
    surfaceBright = Colour.SurfaceBrightLight,
    surfaceContainer = Colour.SurfaceContainerLight,
    surfaceContainerHigh = Colour.SurfaceContainerHighLight,
    surfaceContainerHighest = Colour.SurfaceContainerHighestLight,
    surfaceContainerLow = Colour.SurfaceContainerLowLight,
    surfaceContainerLowest = Colour.SurfaceContainerLowestLight,
    surfaceDim = Colour.SurfaceDimLight,
    primaryFixed = Colour.PrimaryFixed,
    primaryFixedDim = Colour.PrimaryFixedDim,
    onPrimaryFixed = Colour.OnPrimaryFixed,
    onPrimaryFixedVariant = Colour.OnPrimaryFixedVariant,
    secondaryFixed = Colour.SecondaryFixed,
    secondaryFixedDim = Colour.SecondaryFixedDim,
    onSecondaryFixed = Colour.OnSecondaryFixed,
    onSecondaryFixedVariant = Colour.OnSecondaryFixedVariant,
    tertiaryFixed = Colour.TertiaryFixed,
    tertiaryFixedDim = Colour.TertiaryFixedDim,
    onTertiaryFixed = Colour.OnTertiaryFixed,
    onTertiaryFixedVariant = Colour.OnTertiaryFixedVariant,
)

private val DefaultColorScheme = darkColorScheme(
    primary = Colour.PrimaryDark,
    onPrimary = Colour.OnPrimaryDark,
    primaryContainer = Colour.PrimaryContainerDark,
    onPrimaryContainer = Colour.OnPrimaryContainerDark,
    inversePrimary = Colour.InversePrimaryDark,
    secondary = Colour.SecondaryDark,
    onSecondary = Colour.OnSecondaryDark,
    secondaryContainer = Colour.SecondaryContainerDark,
    onSecondaryContainer = Colour.OnSecondaryContainerDark,
    tertiary = Colour.TertiaryDark,
    onTertiary = Colour.OnTertiaryDark,
    tertiaryContainer = Colour.TertiaryContainerDark,
    onTertiaryContainer = Colour.OnTertiaryContainerDark,
    background = Colour.BackgroundDark,
    onBackground = Colour.OnBackgroundDark,
    surface = Colour.SurfaceDark,
    onSurface = Colour.OnSurfaceDark,
    surfaceVariant = Colour.SurfaceVariantDark,
    onSurfaceVariant = Colour.OnSurfaceVariantDark,
    surfaceTint = Colour.SurfaceTintDark,
    inverseSurface = Colour.InverseSurfaceDark,
    inverseOnSurface = Colour.InverseOnSurfaceDark,
    error = Colour.ErrorDark,
    onError = Colour.OnErrorDark,
    errorContainer = Colour.ErrorContainerDark,
    onErrorContainer = Colour.OnErrorContainerDark,
    outline = Colour.OutlineDark,
    outlineVariant = Colour.OutlineVariantDark,
    scrim = Colour.ScrimDark,
    surfaceBright = Colour.SurfaceBrightDark,
    surfaceContainer = Colour.SurfaceContainerDark,
    surfaceContainerHigh = Colour.SurfaceContainerHighDark,
    surfaceContainerHighest = Colour.SurfaceContainerHighestDark,
    surfaceContainerLow = Colour.SurfaceContainerLowDark,
    surfaceContainerLowest = Colour.SurfaceContainerLowestDark,
    surfaceDim = Colour.SurfaceDimDark,
    primaryFixed = Colour.PrimaryFixed,
    primaryFixedDim = Colour.PrimaryFixedDim,
    onPrimaryFixed = Colour.OnPrimaryFixed,
    onPrimaryFixedVariant = Colour.OnPrimaryFixedVariant,
    secondaryFixed = Colour.SecondaryFixed,
    secondaryFixedDim = Colour.SecondaryFixedDim,
    onSecondaryFixed = Colour.OnSecondaryFixed,
    onSecondaryFixedVariant = Colour.OnSecondaryFixedVariant,
    tertiaryFixed = Colour.TertiaryFixed,
    tertiaryFixedDim = Colour.TertiaryFixedDim,
    onTertiaryFixed = Colour.OnTertiaryFixed,
    onTertiaryFixedVariant = Colour.OnTertiaryFixedVariant,
)

val AmoledColorScheme = DefaultColorScheme.copy(
    background = Color(0xff000000),
    onBackground = Color(0xffedece8),
    surfaceVariant = Color(0xff141413),
    onSurfaceVariant = Color(0xffafaaa2),
    surface = Color(0xff000000),
    onSurface = Color(0xffedece8),
    surfaceContainerLowest = Color(0xff000000),
    surfaceContainerLow = Color(0xff0a0a09),
    surfaceContainer = Color(0xff141413),
    surfaceContainerHigh = Color(0xff1c1b1a),
    surfaceContainerHighest = Color(0xff242322),
    outline = Color(0xff3a3834),
    outlineVariant = Color(0xff262523),
)

enum class Theme {
    None,
    Default,
    Light,
    M3Dynamic,
    Amoled
}

@Composable
fun getColorScheme(
    requestedTheme: Theme,
    colourOverrides: OverridableColourScheme? = null
): ColorScheme {
    val context = LocalContext.current

    val systemInDarkTheme = isSystemInDarkTheme()
    val m3Supported = systemSupportsDynamicColors()

    val colorScheme = when {
        m3Supported && requestedTheme == Theme.M3Dynamic -> dynamicDarkColorScheme(context)
        requestedTheme == Theme.Default -> DefaultColorScheme
        requestedTheme == Theme.Light -> LightColorScheme
        requestedTheme == Theme.Amoled -> AmoledColorScheme
        requestedTheme == Theme.None -> DefaultColorScheme
        else -> DefaultColorScheme
    }.copy()

    val colorSchemeIsDark = when {
        m3Supported && requestedTheme == Theme.M3Dynamic -> isSystemInDarkTheme()
        requestedTheme == Theme.Default -> true
        requestedTheme == Theme.Light -> false
        requestedTheme == Theme.Amoled -> true
        requestedTheme == Theme.None && systemInDarkTheme -> true
        requestedTheme == Theme.None && !systemInDarkTheme -> false
        else -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
                !colorSchemeIsDark
        }
    }

    if (colourOverrides == null) return colorScheme
    return colourOverrides.applyTo(colorScheme)
}

@SuppressLint("NewApi")
@Composable
fun StoatTheme(
    requestedTheme: Theme,
    requestedUserInterfaceFont: UserInterfaceFont,
    colourOverrides: OverridableColourScheme? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(requestedTheme, colourOverrides)
    val typography = when (requestedUserInterfaceFont) {
        UserInterfaceFont.Default -> StoatTypography
        UserInterfaceFont.GoogleSansFlex -> GoogleTypography
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

fun systemSupportsDynamicColors(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

fun getDefaultTheme(): Theme {
    return Theme.Default
}

fun isThemeDark(theme: Theme, systemIsDark: Boolean): Boolean {
    return when (theme) {
        Theme.Default, Theme.Amoled -> true
        Theme.Light -> false
        Theme.M3Dynamic, Theme.None -> systemIsDark
    }
}

@Composable
fun isThemeDark(theme: Theme) = isThemeDark(theme, isSystemInDarkTheme())