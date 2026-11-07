package ua.ecowi.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.ecowi.R
import ua.ecowi.appwidget.Widgets
import ua.ecowi.core.DisplayMode
import ua.ecowi.data.AppSettings
import ua.ecowi.data.WidgetAppearance
import ua.ecowi.data.WidgetTheme
import ua.ecowi.ui.components.AppearanceEditor
import ua.ecowi.ui.components.EcoCard
import ua.ecowi.ui.components.PrimaryButton
import ua.ecowi.ui.components.SectionLabel
import ua.ecowi.ui.components.Segmented
import ua.ecowi.ui.components.WidgetPreview
import ua.ecowi.ui.theme.EcoWiTheme
import ua.ecowi.ui.theme.LocalEco
import ua.ecowi.work.RefreshScheduler

/**
 * Per-widget options: which consumption matters more (or alternate), theme and background
 * opacity. Declared `reconfigurable|configuration_optional`: One UI may skip it on placement
 * (then the app defaults apply); long-press the widget → Settings to open it later.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val settings = AppSettings(this)
        setContent {
            EcoWiTheme {
                ConfigScreen(
                    initialMode = settings.displayMode(widgetId),
                    initialCycle = settings.cycleSeconds(widgetId),
                    initialAppearance = settings.appearance(widgetId),
                    configured = settings.isConfigured,
                    onOpenApp = { startActivity(Intent(this, MainActivity::class.java)) },
                    onDone = ::finishWith,
                )
            }
        }
    }

    private fun finishWith(mode: DisplayMode, cycleSeconds: Int, appearance: WidgetAppearance) {
        AppSettings(this).saveWidget(widgetId, mode, cycleSeconds, appearance)
        val manager = AppWidgetManager.getInstance(this)
        Widgets.kindOf(this, widgetId)?.let { Widgets.render(this, manager, widgetId, it) }
        RefreshScheduler.ensureScheduled(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun ConfigScreen(
    initialMode: DisplayMode,
    initialCycle: Int,
    initialAppearance: WidgetAppearance,
    configured: Boolean,
    onOpenApp: () -> Unit,
    onDone: (DisplayMode, Int, WidgetAppearance) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var cycle by rememberSaveable { mutableIntStateOf(initialCycle) }
    var appearance by rememberSaveable(stateSaver = AppearanceSaver) { mutableStateOf(initialAppearance) }
    val eco = LocalEco.current

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.title_config), style = MaterialTheme.typography.headlineMedium)

            if (!configured) {
                EcoCard(highlighted = true) {
                    Text(stringResource(R.string.config_no_keys), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenApp) { Text(stringResource(R.string.action_open_app)) }
                }
            }

            WidgetPreview(appearance)

            SectionLabel(stringResource(R.string.config_question))
            EcoCard {
                DisplayMode.entries.forEach { m ->
                    val on = m == mode
                    EcoCard(onClick = { mode = m }, highlighted = on, padding = 14.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            stringResource(
                                when (m) {
                                    DisplayMode.PREFER_AC -> R.string.mode_ac
                                    DisplayMode.PREFER_DC -> R.string.mode_dc
                                    DisplayMode.CYCLE -> R.string.mode_cycle
                                },
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(
                                when (m) {
                                    DisplayMode.PREFER_AC -> R.string.mode_ac_desc
                                    DisplayMode.PREFER_DC -> R.string.mode_dc_desc
                                    DisplayMode.CYCLE -> R.string.mode_cycle_desc
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = eco.muted,
                        )
                    }
                }
                if (mode == DisplayMode.CYCLE) {
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        options = AppSettings.CYCLE_CHOICES.map { it to stringResource(R.string.cycle_every, it) },
                        selected = cycle,
                        onSelect = { cycle = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionLabel(stringResource(R.string.appearance_section))
            EcoCard { AppearanceEditor(appearance) { appearance = it } }

            Row { PrimaryButton(stringResource(R.string.action_done), { onDone(mode, cycle, appearance) }, Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** rememberSaveable support for [WidgetAppearance] (survives rotation). */
private val AppearanceSaver = Saver<WidgetAppearance, List<Any>>(
    save = { listOf(it.theme.name, it.backgroundOpacity, it.dynamicColor) },
    restore = { WidgetAppearance(WidgetTheme.valueOf(it[0] as String), it[1] as Int, it[2] as Boolean) },
)
