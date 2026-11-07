package ua.ecowi.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.ecowi.R
import ua.ecowi.data.WidgetAppearance
import ua.ecowi.data.WidgetTheme
import ua.ecowi.ui.theme.LocalEco
import kotlin.math.roundToInt

/** Theme (system / light / dark), background opacity and Material You toggle for widgets. */
@Composable
fun AppearanceEditor(value: WidgetAppearance, onChange: (WidgetAppearance) -> Unit) {
    val eco = LocalEco.current
    Column {
        Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Segmented(
            options = listOf(
                WidgetTheme.SYSTEM to stringResource(R.string.theme_system),
                WidgetTheme.LIGHT to stringResource(R.string.theme_light),
                WidgetTheme.DARK to stringResource(R.string.theme_dark),
            ),
            selected = value.theme,
            onSelect = { onChange(value.copy(theme = it)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.appearance_opacity), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${value.backgroundOpacity}%", style = MaterialTheme.typography.labelLarge, color = eco.muted)
        }
        Slider(
            value = value.backgroundOpacity.toFloat(),
            onValueChange = { onChange(value.copy(backgroundOpacity = (it / 5f).roundToInt() * 5)) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        if (Build.VERSION.SDK_INT >= 31) {
            ToggleRow(
                stringResource(R.string.appearance_dynamic),
                stringResource(R.string.appearance_dynamic_desc),
                value.dynamicColor,
                { onChange(value.copy(dynamicColor = it)) },
            )
        }
    }
}
