package pe.combustiblehoy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Paleta propia (no dinámica): el verde de marca identifica la app y el widget en cualquier teléfono,
 * y garantiza el contraste de los colores de precio (sube/baja) que con Material You no se controla.
 */

val BrandLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4F1D3),
    onPrimaryContainer = Color(0xFF002115),
    inversePrimary = Color(0xFF8BD6B4),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FBF7),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF5FBF7),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = Color(0xFF0B6E4F),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C2),
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFECF2EE),
    surfaceBright = Color(0xFFF5FBF7),
    surfaceDim = Color(0xFFD6DBD8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE4EAE5),
    surfaceContainerHighest = Color(0xFFDEE4E0),
)

val BrandDark: ColorScheme = darkColorScheme(
    primary = Color(0xFF8BD6B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFB4F1D3),
    inversePrimary = Color(0xFF0B6E4F),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFFFB86F),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceTint = Color(0xFF8BD6B4),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2C322F),
    surfaceBright = Color(0xFF353B38),
    surfaceDim = Color(0xFF0F1512),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/** Colores semánticos de precio, con contraste AA en claro y oscuro. */
@Immutable
data class PriceColors(
    val up: Color,
    val upContainer: Color,
    val onUpContainer: Color,
    val down: Color,
    val downContainer: Color,
    val onDownContainer: Color,
)

private val LightPriceColors = PriceColors(
    up = Color(0xFFB3261E),
    upContainer = Color(0xFFFFDAD6),
    onUpContainer = Color(0xFF410002),
    down = Color(0xFF1B6C2F),
    downContainer = Color(0xFFC6F0C8),
    onDownContainer = Color(0xFF002109),
)

private val DarkPriceColors = PriceColors(
    up = Color(0xFFFFB4AB),
    upContainer = Color(0xFF93000A),
    onUpContainer = Color(0xFFFFDAD6),
    down = Color(0xFF9AD7A0),
    downContainer = Color(0xFF0B5321),
    onDownContainer = Color(0xFFC6F0C8),
)

val LocalPriceColors = staticCompositionLocalOf { LightPriceColors }

/** Números tabulares: los precios quedan alineados en columna y no "bailan" al cambiar. */
val PriceStyle = TextStyle(fontFeatureSettings = "tnum", fontWeight = FontWeight.Bold)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CombustibleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPriceColors provides if (dark) DarkPriceColors else LightPriceColors) {
        MaterialTheme(
            colorScheme = if (dark) BrandDark else BrandLight,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
