package ua.ecowi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape
import ua.ecowi.R
import ua.ecowi.appwidget.Widgets
import ua.ecowi.data.AppSettings
import ua.ecowi.ui.components.AppearanceEditor
import ua.ecowi.ui.components.EcoCard
import ua.ecowi.ui.components.EcoIcon
import ua.ecowi.ui.components.PrimaryButton
import ua.ecowi.ui.components.SectionLabel
import ua.ecowi.ui.components.WidgetPreview
import ua.ecowi.ui.theme.LocalEco

@Composable
fun WidgetsScreen() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    var appearance by remember { mutableStateOf(settings.defaultAppearance) }
    var saved by remember { mutableStateOf(false) }
    val eco = LocalEco.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_widgets), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.widgets_intro), style = MaterialTheme.typography.bodyMedium, color = eco.muted)

        WidgetPreview(appearance)

        EcoCard {
            Text(stringResource(R.string.widgets_defaults), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.widgets_defaults_desc), style = MaterialTheme.typography.bodySmall, color = eco.muted)
            Spacer(Modifier.height(14.dp))
            AppearanceEditor(appearance) { appearance = it; saved = false }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    stringResource(if (saved) R.string.saved else R.string.action_save),
                    onClick = {
                        settings.defaultAppearance = appearance
                        saved = true
                    },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        settings.defaultAppearance = appearance
                        for (kind in Widgets.Kind.entries) {
                            Widgets.ids(context, kind).forEach { id ->
                                settings.saveWidget(id, settings.displayMode(id), settings.cycleSeconds(id), appearance)
                            }
                        }
                        Widgets.renderAll(context)
                        saved = true
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(R.string.widgets_apply_all), style = MaterialTheme.typography.labelLarge) }
            }
        }

        SectionLabel(stringResource(R.string.widgets_how))
        HowTo(R.drawable.ic_nav_widgets, R.string.how_bar_title, R.string.how_bar_text)
        HowTo(R.drawable.ic_nav_station, R.string.how_ring_title, R.string.how_ring_text)
        HowTo(R.drawable.ic_bolt, R.string.how_lock_title, R.string.how_lock_text)
        HowTo(R.drawable.ic_nav_bell, R.string.how_nowbar_title, R.string.how_nowbar_text)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HowTo(@DrawableRes icon: Int, title: Int, text: Int) {
    EcoCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            EcoIcon(icon, MaterialTheme.colorScheme.primary, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = LocalEco.current.muted)
            }
        }
    }
}
