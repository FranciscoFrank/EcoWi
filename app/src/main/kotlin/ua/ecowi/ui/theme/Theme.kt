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

/** "Volt": graphite surfaces, mint → lime energy gradient, amber for the bolt. */
object Volt {
    val Mint = Color(0xFF2CF5D0)
    val Lime = Color(0xFFC8F53B)
    val Amber = Color(0xFFFFD23F)
    val Orange = Color(0xFFFF9F43)
    val Red = Color(0xFFFF5C5C)

    val Graphite0 = Color(0xFF0B0D10)
    val Graphite1 = Color(0xFF12151A)
    val Graphite2 = Color(0xFF1A1E24)
    val Graphite3 = Color(0xFF242932)
    val Line = Color(0xFF2A3038)

    val energy = Brush.linearGradient(listOf(Mint, Lime))
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
    EcoColors(Volt.Lime, Volt.Line, Color.Gray, Volt.Lime, Volt.Amber, Volt.Orange, Volt.Red, true)
}

private val Unbounded = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold),
)

private val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

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
    primary = Volt.Lime,
    onPrimary = Volt.Graphite0,
    primaryContainer = Color(0xFF2B3A08),
    onPrimaryContainer = Volt.Lime,
    secondary = Volt.Mint,
    onSecondary = Volt.Graphite0,
    tertiary = Volt.Amber,
    onTertiary = Volt.Graphite0,
    background = Volt.Graphite0,
    onBackground = Color(0xFFEEF1F4),
    surface = Volt.Graphite1,
    onSurface = Color(0xFFEEF1F4),
    surfaceVariant = Volt.Graphite2,
    onSurfaceVariant = Color(0xFF9AA3AD),
    surfaceContainer = Volt.Graphite1,
    surfaceContainerHigh = Volt.Graphite2,
    surfaceContainerHighest = Volt.Graphite3,
    outline = Volt.Line,
    outlineVariant = Color(0xFF20252C),
    error = Volt.Red,
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF3F7A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF6A8),
    onPrimaryContainer = Color(0xFF1C3300),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = Color(0xFFB98800),
    background = Color(0xFFF3F5F0),
    onBackground = Color(0xFF12151A),
    surface = Color.White,
    onSurface = Color(0xFF12151A),
    surfaceVariant = Color(0xFFE9ECE5),
    onSurfaceVariant = Color(0xFF5D6670),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2EC),
    surfaceContainerHighest = Color(0xFFE6E9E2),
    outline = Color(0xFFD6DBD2),
    outlineVariant = Color(0xFFE4E8E0),
    error = Color(0xFFD32F2F),
)

@Composable
fun EcoWiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val eco = if (dark) {
        EcoColors(Volt.Lime, Volt.Line, Color(0xFF9AA3AD), Volt.Lime, Volt.Amber, Volt.Orange, Volt.Red, true)
    } else {
        EcoColors(Color(0xFF3F7A00), Color(0xFFDCE1D8), Color(0xFF5D6670), Color(0xFF4E9A16), Color(0xFFD99A00), Color(0xFFE36A00), Color(0xFFD32F2F), false)
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
