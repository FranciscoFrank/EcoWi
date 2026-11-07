package ua.ecowi.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import ua.ecowi.R
import ua.ecowi.data.AppSettings
import ua.ecowi.data.DataSourceKind
import ua.ecowi.live.LiveService
import ua.ecowi.ui.StationUi
import ua.ecowi.ui.components.EcoCard
import ua.ecowi.ui.components.PrimaryButton
import ua.ecowi.ui.components.SectionLabel
import ua.ecowi.ui.components.Segmented
import ua.ecowi.ui.components.StatusPill
import ua.ecowi.ui.components.ToggleRow
import ua.ecowi.ui.theme.LocalEco
import ua.ecowi.work.RefreshScheduler

@Composable
fun AlertsScreen(ui: StationUi) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val eco = LocalEco.current
    var refresh by remember { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(settings.eventConfig) }
    var live by remember { mutableStateOf(settings.liveMode) }
    var interval by remember { mutableIntStateOf(settings.refreshIntervalMin) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notificationsAllowed = remember(refresh) { notificationsAllowed(context) }
    val batteryFree = remember(refresh) { ignoresBatteryOptimizations(context) }
    val accountSource = settings.source == DataSourceKind.APP_ACCOUNT

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else openAppNotificationSettings(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_alerts), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))

        if (!notificationsAllowed) {
            EcoCard(highlighted = true) {
                Text(stringResource(R.string.alerts_permission_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.alerts_permission_text), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.alerts_permission_action), ::requestNotifications, Modifier.fillMaxWidth())
            }
        }

        // ---- live mode
        EcoCard {
            ToggleRow(
                stringResource(R.string.live_mode_title),
                stringResource(if (accountSource) R.string.live_mode_desc else R.string.live_mode_needs_account),
                live,
                { on ->
                    live = on
                    settings.liveMode = on
                    if (on && !notificationsAllowed) requestNotifications()
                    LiveService.sync(context)
                },
                enabled = accountSource,
            )
            if (live) {
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    stringResource(if (ui.live) R.string.live_running else R.string.live_starting),
                    eco.good,
                )
            } else {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = eco.cardBorder)
                Text(stringResource(R.string.refresh_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.refresh_desc), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = AppSettings.INTERVAL_CHOICES.map { it to stringResource(R.string.minutes_short, it) },
                    selected = interval,
                    onSelect = {
                        interval = it
                        settings.refreshIntervalMin = it
                        RefreshScheduler.ensureScheduled(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!batteryFree) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = eco.cardBorder)
                Text(stringResource(R.string.battery_opt_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.battery_opt_text), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { requestIgnoreBatteryOptimizations(context); refresh++ },
                    shape = CircleShape,
                ) { Text(stringResource(R.string.battery_opt_action)) }
            }
            if (Build.VERSION.SDK_INT >= 36 && live && !canPromote(context)) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = eco.cardBorder)
                Text(stringResource(R.string.nowbar_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.nowbar_text), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openAppNotificationSettings(context) }, shape = CircleShape) {
                    Text(stringResource(R.string.nowbar_action))
                }
            }
        }

        SectionLabel(stringResource(R.string.alerts_section))
        EcoCard {
            ToggleRow(stringResource(R.string.alert_low), stringResource(R.string.alert_low_desc), config.notifyLow, {
                config = config.copy(notifyLow = it); settings.eventConfig = config
            })
            ToggleRow(stringResource(R.string.alert_full), stringResource(R.string.alert_full_desc), config.notifyFull, {
                config = config.copy(notifyFull = it); settings.eventConfig = config
            })
            ToggleRow(stringResource(R.string.alert_charging), stringResource(R.string.alert_charging_desc), config.notifyChargingStarted, {
                config = config.copy(notifyChargingStarted = it); settings.eventConfig = config
            })
            ToggleRow(stringResource(R.string.alert_grid), stringResource(R.string.alert_grid_desc), config.notifyGrid, {
                config = config.copy(notifyGrid = it); settings.eventConfig = config
            })
            ToggleRow(stringResource(R.string.alert_ports), stringResource(R.string.alert_ports_desc), config.notifyPorts, {
                config = config.copy(notifyPorts = it); settings.eventConfig = config
            })
            ToggleRow(stringResource(R.string.alert_link), stringResource(R.string.alert_link_desc), config.notifyLink, {
                config = config.copy(notifyLink = it); settings.eventConfig = config
            })
        }
        Text(
            stringResource(if (live) R.string.alerts_footer_live else R.string.alerts_footer_periodic),
            style = MaterialTheme.typography.bodySmall,
            color = eco.muted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun notificationsAllowed(c: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    return c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
}

private fun ignoresBatteryOptimizations(c: Context): Boolean =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

@SuppressLint("BatteryLife") // personal app: a persistent connection is the whole point of live mode
private fun requestIgnoreBatteryOptimizations(c: Context) {
    runCatching {
        c.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")))
    }.onFailure {
        c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

private fun canPromote(c: Context): Boolean =
    Build.VERSION.SDK_INT < 36 || c.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

private fun openAppNotificationSettings(c: Context) {
    c.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
    )
}
