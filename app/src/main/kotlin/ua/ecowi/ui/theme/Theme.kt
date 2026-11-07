package ua.ecowi.ui.theme

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ecowi.R

/**
 * "Neon": the palette of the EcoWi logo, used everywhere (app, widgets, notifications, icon).
 * Mirrored in res/values/colors.xml (widgets, notifications) and tools/logo/render.py (icon); see
 * docs/BRAND.md when changing a value.
 */
object Neon {
    // the neon tube of the logo
    val Glow = Color(0xFF9EDC4E)
    val Tube = Color(0xFFC9EE85)
    val Bolt = Color(0xFFDCFB90)
    val Core = Color(0xFFEBFEB3)

    // the wall behind the tube (icon background) and the dark surfaces derived from it
    val WallTop = Color(0xFF4A5057)
    val WallBottom = Color(0xFF1F2328)
    val Graphite0 = Color(0xFF15181C)
    val Graphite1 = Color(0xFF1C2025)
    val Graphite2 = Color(0xFF23282E)
    val Graphite3 = Color(0xFF2C3239)
    val Line = Color(0xFF343B43)
    val Text = Color(0xFFEEF1F4)
    val Muted = Color(0xFF9AA3AD)

    // light theme counterparts (the tube colour is too pale on white)
    val LeafDark = Color(0xFF4C7F12)
    val LeafLight = Color(0xFF6FA82A)

    // states (battery bands, warnings)
    val Amber = Color(0xFFFFD23F)
    val Orange = Color(0xFFFF9F43)
    val Red = Color(0xFFFF5C5C)

    val energy = Brush.linearGradient(listOf(Glow, Bolt))
}

/** Extra brand colours not covered by Material's ColorScheme. */
@Immutable
data class EcoColors(
    val glow: Color,
    val cardBorder: Color,
    val muted: Color,
    val good: Color,
    val warn: Color,
    val low: Color,
    val bad: Color,
    val isDark: Boolean,
)

val LocalEco = staticCompositionLocalOf {
    EcoColors(Neon.Tube, Neon.Line, Neon.Muted, Neon.Tube, Neon.Amber, Neon.Orange, Neon.Red, true)
}

internal val Unbounded = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
)

internal val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/**
 * Text styles of the home-screen widgets, mirrored from res/values/styles.xml (EW.Widget.*) so the in-app
 * preview looks like the real widget: Unbounded for the percentage (the widgets draw it themselves, see
 * appwidget/SocText.kt) and the system font for everything else, because launchers don't load app fonts.
 */
object WidgetType {
    val soc = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val loadValue = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp)
    val loadLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 11.sp)
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 60.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.6.sp),
)

private val darkScheme: ColorScheme = darkColorScheme(
    primary = Neon.Tube,
    onPrimary = Neon.Graphite0,
    primaryContainer = Color(0xFF2F3D1C),
    onPrimaryContainer = Neon.Bolt,
    secondary = Neon.Glow,
    onSecondary = Neon.Graphite0,
    tertiary = Neon.Amber,
    onTertiary = Neon.Graphite0,
    background = Neon.Graphite0,
    onBackground = Neon.Text,
    surface = Neon.Graphite1,
    onSurface = Neon.Text,
    surfaceVariant = Neon.Graphite2,
    onSurfaceVariant = Neon.Muted,
    surfaceContainer = Neon.Graphite1,
    surfaceContainerHigh = Neon.Graphite2,
    surfaceContainerHighest = Neon.Graphite3,
    outline = Neon.Line,
    outlineVariant = Color(0xFF262B31),
    error = Neon.Red,
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Neon.LeafDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2F4C3),
    onPrimaryContainer = Color(0xFF1C3300),
    secondary = Neon.LeafLight,
    onSecondary = Color.White,
    tertiary = Color(0xFFB98800),
    background = Color(0xFFF4F6F1),
    onBackground = Neon.Graphite0,
    surface = Color.White,
    onSurface = Neon.Graphite0,
    surfaceVariant = Color(0xFFE9ECE5),
    onSurfaceVariant = Color(0xFF5D6670),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2EC),
    surfaceContainerHighest = Color(0xFFE6E9E2),
    outline = Color(0xFFD6DCD2),
    outlineVariant = Color(0xFFE4E8E0),
    error = Color(0xFFD32F2F),
)

@Composable
fun EcoWiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val eco = if (dark) {
        EcoColors(Neon.Tube, Neon.Line, Neon.Muted, Neon.Tube, Neon.Amber, Neon.Orange, Neon.Red, true)
    } else {
        EcoColors(Neon.LeafDark, Color(0xFFDCE1D8), Color(0xFF5D6670), Color(0xFF4C8A12), Color(0xFFC98F00), Color(0xFFE36A00), Color(0xFFD32F2F), false)
    }
    CompositionLocalProvider(LocalEco provides eco) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = typography,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
