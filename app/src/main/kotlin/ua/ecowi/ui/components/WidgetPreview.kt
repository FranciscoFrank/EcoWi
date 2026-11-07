package ua.ecowi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.ecowi.R
import ua.ecowi.appwidget.textWidthEm
import ua.ecowi.ui.theme.WidgetType
import ua.ecowi.appwidget.Palette
import ua.ecowi.core.BatteryBand
import ua.ecowi.data.WidgetAppearance
import ua.ecowi.data.WidgetTheme
import ua.ecowi.ui.theme.LocalEco
import ua.ecowi.ui.theme.Neon

/**
 * In-app preview of how a widget will look with an appearance (theme, opacity, colours),
 * drawn over a fake wallpaper so transparency is visible. Uses the same palettes as the widgets.
 */
@Composable
fun WidgetPreview(appearance: WidgetAppearance, soc: Int = 78, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = when (appearance.theme) {
        WidgetTheme.LIGHT -> false
        WidgetTheme.DARK -> true
        WidgetTheme.SYSTEM -> LocalEco.current.isDark
    }
    val p = if (dark) Palette.dark(context, appearance.dynamicColor) else Palette.light(context, appearance.dynamicColor)
    val alpha = appearance.backgroundOpacity.coerceIn(0, 100) / 100f
    val band = Color(p.band(BatteryBand.of(soc)))

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                // a neutral stand-in for the wallpaper, lit like the logo's wall
                Brush.linearGradient(listOf(Neon.WallTop, Neon.WallBottom, Color(0xFF2E3A2A))),
            )
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // bar widget
            Row(
                Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color(p.bg).copy(alpha = alpha)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$soc%", color = Color(p.text), style = WidgetType.soc, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(p.track))) {
                        Box(Modifier.fillMaxWidth(soc / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(band))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(R.string.preview_caption), color = Color(p.textSecondary), style = WidgetType.caption, maxLines = 1)
                }
                Spacer(Modifier.width(12.dp))
                EcoIcon(R.drawable.ic_ac, Color(p.accent), 20.dp)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(stringResource(R.string.preview_load), color = Color(p.text), style = WidgetType.loadValue, maxLines = 1)
                    Text(stringResource(R.string.label_ac), color = Color(p.textSecondary), style = WidgetType.loadLabel, maxLines = 1)
                }
            }
            // ring + mini
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniRing(p, alpha, soc, 112, small = false)
                MiniRing(p, alpha, soc, 80, small = true)
            }
        }
    }
}

/**
 * Mirrors the ring widgets: [small] = the 1×1 (square card, 30 % corners, thick ring 6dp inside, text sized
 * like [smallRingTextDp]); otherwise the 2×2 ring (thin ring, 22sp text).
 */
@Composable
private fun MiniRing(p: Palette, alpha: Float, soc: Int, sizeDp: Int, small: Boolean) {
    val text = "$soc%"
    Box(
        Modifier.size(sizeDp.dp).clip(if (small) RoundedCornerShape(30) else RoundedCornerShape(26.dp))
            .background(Color(p.bg).copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        val band = Color(p.band(BatteryBand.of(soc)))
        val track = Color(p.track)
        val ringDp = if (small) sizeDp - 12f else sizeDp * 0.78f
        Canvas(Modifier.size(ringDp.dp)) {
            val sw = size.minDimension * if (small) 0.13f else 0.085f
            val inset = sw / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            drawArc(band, -90f, 360f * soc / 100f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        val style = if (small) {
            val inner = (sizeDp - 12f) * (1f - 2 * 0.13f)
            val dp = (inner * 0.8f / textWidthEm(text)).coerceIn(9f, 28f)
            WidgetType.soc.copy(fontSize = with(LocalDensity.current) { dp.dp.toSp() })
        } else {
            WidgetType.soc
        }
        Text(text, color = Color(p.text), style = style, maxLines = 1)
    }
}
