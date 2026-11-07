package ua.ecowi.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.ecowi.R
import ua.ecowi.appwidget.Format
import ua.ecowi.appwidget.Widgets
import ua.ecowi.core.ChargeState
import ua.ecowi.core.EventDetector
import ua.ecowi.core.Port
import ua.ecowi.core.PowerSnapshot
import ua.ecowi.data.DataSource
import ua.ecowi.ui.StationUi
import ua.ecowi.ui.components.ChargeGauge
import ua.ecowi.ui.components.EcoCard
import ua.ecowi.ui.components.EcoIcon
import ua.ecowi.ui.components.PortChip
import ua.ecowi.ui.components.PrimaryButton
import ua.ecowi.ui.components.SectionLabel
import ua.ecowi.ui.components.StatTile
import ua.ecowi.ui.components.StatusPill
import ua.ecowi.ui.components.bandColor
import ua.ecowi.ui.theme.LocalEco
import ua.ecowi.work.RefreshScheduler
import java.util.Date

@Composable
fun StationScreen(ui: StationUi, onOpenConnection: () -> Unit) {
    val context = LocalContext.current
    val snap = ui.cached.snapshot
    val eco = LocalEco.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            ConnectionPill(ui)
            IconButton(onClick = {
                Widgets.renderAll(context, refreshing = true)
                RefreshScheduler.refreshNow(context)
            }) { EcoIcon(R.drawable.ic_refresh, eco.muted) }
        }

        if (!ui.configured) {
            EcoCard(highlighted = true) {
                Text(stringResource(R.string.onboard_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.onboard_text), style = MaterialTheme.typography.bodyMedium, color = eco.muted)
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.onboard_action), onOpenConnection, Modifier.fillMaxWidth())
            }
        }

        // ---- hero ring
        EcoCard(padding = 20.dp) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ChargeGauge(snap?.socPercent, Modifier.size(250.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (snap?.isCharging == true) EcoIcon(R.drawable.ic_bolt, MaterialTheme.colorScheme.tertiary, 22.dp)
                        Text(
                            snap?.socPercent?.let { "$it%" } ?: "—",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stateLabel(snap),
                            style = MaterialTheme.typography.labelLarge,
                            color = bandColor(snap?.socPercent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                remainingText(snap)?.let { StatusPill(it, MaterialTheme.colorScheme.secondary, dot = false) }
                snap?.gridConnected?.let { on ->
                    StatusPill(
                        stringResource(if (on) R.string.grid_on else R.string.grid_off),
                        if (on) eco.good else eco.warn,
                    )
                }
                snap?.chargeLimitPercent?.takeIf { it < 100 }?.let {
                    StatusPill(stringResource(R.string.charge_limit, it), eco.muted, dot = false)
                }
            }
        }

        // ---- flows
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val inW = snap?.totalInW
            StatTile(
                if ((snap?.solarInW ?: 0) > (snap?.acInW ?: 0)) R.drawable.ic_sun else R.drawable.ic_grid,
                stringResource(R.string.stat_input), inW?.let { Format.watts(context, it) } ?: "—",
                Modifier.weight(1f), accent = MaterialTheme.colorScheme.secondary, active = (inW ?: 0) > 1,
            )
            val outW = snap?.totalOutW ?: listOfNotNull(snap?.acOutW, snap?.dcOutW).takeIf { it.isNotEmpty() }?.sum()
            StatTile(
                R.drawable.ic_ac, stringResource(R.string.stat_output), outW?.let { Format.watts(context, it) } ?: "—",
                Modifier.weight(1f), active = (outW ?: 0) > 1,
            )
        }

        // ---- ports
        SectionLabel(stringResource(R.string.section_ports))
        val ports = snap?.ports?.keys?.sortedBy { it.ordinal }?.takeIf { it.isNotEmpty() }
            ?: listOf(Port.AC, Port.USB_C1, Port.USB_C2, Port.USB_A1)
        ports.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { port ->
                    val w = snap?.ports?.get(port)
                    PortChip(
                        Format.portName(context, port),
                        w?.let { Format.watts(context, it) } ?: "—",
                        active = (w ?: 0) >= EventDetector.threshold(port),
                        icon = if (port == Port.AC) R.drawable.ic_ac else R.drawable.ic_dc,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ---- footer
        ui.cached.lastError?.let { err ->
            EcoCard {
                Text(stringResource(R.string.last_error), style = MaterialTheme.typography.titleSmall, color = eco.warn)
                Text(err, style = MaterialTheme.typography.bodySmall, color = eco.muted)
            }
        }
        Text(
            footerText(context, ui),
            style = MaterialTheme.typography.bodySmall,
            color = eco.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
        )
    }
}

@Composable
private fun ConnectionPill(ui: StationUi) {
    val eco = LocalEco.current
    val snap = ui.cached.snapshot
    val fresh = snap != null && System.currentTimeMillis() - snap.fetchedAtMs < 60_000
    when {
        !ui.configured -> Unit
        ui.live && fresh -> StatusPill(stringResource(R.string.pill_live), eco.good)
        ui.cached.lastError != null && snap == null -> StatusPill(stringResource(R.string.pill_error), eco.bad)
        else -> StatusPill(stringResource(if (ui.live) R.string.pill_reconnecting else R.string.pill_periodic), eco.muted)
    }
}

@Composable
private fun stateLabel(s: PowerSnapshot?): String = stringResource(
    when {
        s == null -> R.string.state_unknown
        s.isCharging -> R.string.state_charging
        s.chargeState == ChargeState.DISCHARGING || (s.totalOutW ?: 0) > 1 -> R.string.state_discharging
        else -> R.string.state_idle
    },
)

@Composable
private fun remainingText(s: PowerSnapshot?): String? {
    val context = LocalContext.current
    s ?: return null
    return if (s.isCharging) {
        s.chargeRemainMin?.let { stringResource(R.string.remain_to_full, Format.duration(context, it)) }
    } else {
        s.dischargeRemainMin?.let { stringResource(R.string.remain_left, Format.duration(context, it)) }
    }
}

private fun footerText(context: android.content.Context, ui: StationUi): String {
    val snap = ui.cached.snapshot ?: return ""
    val time = DateFormat.getTimeFormat(context).format(Date(snap.fetchedAtMs))
    val src = when (ui.cached.source) {
        DataSource.APP_MQTT -> context.getString(R.string.source_app)
        DataSource.REST, DataSource.OPEN_MQTT -> context.getString(R.string.source_dev)
        null -> ""
    }
    return context.getString(R.string.footer_updated, time, src)
}
