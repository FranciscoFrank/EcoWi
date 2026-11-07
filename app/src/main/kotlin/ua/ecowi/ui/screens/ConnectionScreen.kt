package ua.ecowi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import ua.ecowi.core.StationModels
import ua.ecowi.R
import ua.ecowi.appwidget.Format
import ua.ecowi.appwidget.Widgets
import ua.ecowi.data.AccountCredentials
import ua.ecowi.data.ApiRegion
import ua.ecowi.data.AppSettings
import ua.ecowi.data.Credentials
import ua.ecowi.data.DataSourceKind
import ua.ecowi.data.DeviceInfo
import ua.ecowi.data.EcoFlowOpenApi
import ua.ecowi.data.EventStateStore
import ua.ecowi.data.LinkStore
import ua.ecowi.data.FetchResult
import ua.ecowi.data.SnapshotCache
import ua.ecowi.data.StationRepository
import ua.ecowi.live.LiveService
import ua.ecowi.ui.components.EcoCard
import ua.ecowi.ui.components.EcoField
import ua.ecowi.ui.components.PrimaryButton
import ua.ecowi.ui.components.SectionLabel
import ua.ecowi.ui.components.Segmented
import ua.ecowi.ui.theme.LocalEco
import ua.ecowi.work.RefreshScheduler

@Composable
fun ConnectionScreen() {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val eco = LocalEco.current
    val scope = rememberCoroutineScope()

    var source by rememberSaveable { mutableStateOf(settings.source) }
    val savedAccount = remember { settings.account }
    val savedDev = remember { settings.credentials }

    var email by rememberSaveable { mutableStateOf(savedAccount?.email.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(savedAccount?.password.orEmpty()) }
    var accSn by rememberSaveable { mutableStateOf(savedAccount?.serialNumber.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(savedAccount?.host ?: AccountCredentials.DEFAULT_APP_HOST) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    var region by rememberSaveable { mutableStateOf(savedDev?.region ?: ApiRegion.EUROPE) }
    var accessKey by rememberSaveable { mutableStateOf(savedDev?.accessKey.orEmpty()) }
    var secretKey by rememberSaveable { mutableStateOf(savedDev?.secretKey.orEmpty()) }
    var devSn by rememberSaveable { mutableStateOf(savedDev?.serialNumber.orEmpty()) }
    var devices by remember { mutableStateOf<List<DeviceInfo>?>(null) }
    var modelId by remember { mutableStateOf(settings.stationModelId) }
    var detectedModel by remember { mutableStateOf(settings.detectedModelId) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<FetchResult?>(null) }
    var showRaw by remember { mutableStateOf(false) }

    fun account() = AccountCredentials(email.trim(), password, accSn.trim().uppercase(), host.trim().ifBlank { AccountCredentials.DEFAULT_APP_HOST })
    fun developer() = Credentials(accessKey.trim(), secretKey.trim(), devSn.trim(), region)
    val complete = if (source == DataSourceKind.APP_ACCOUNT) account().isComplete else developer().isComplete
    val savedText = stringResource(R.string.saved)
    val fillAll = stringResource(R.string.fill_all)

    fun save() {
        if (!complete) {
            status = fillAll
            return
        }
        val oldSn = settings.serialNumber
        settings.source = source
        if (source == DataSourceKind.APP_ACCOUNT) settings.account = account() else settings.credentials = developer()
        if (oldSn != settings.serialNumber) {
            SnapshotCache(context).clear()
            EventStateStore(context).reset()
            LinkStore(context).reset()
            settings.detectedModelId = null
            settings.modelProbeAtMs = 0L
        }
        RefreshScheduler.ensureScheduled(context)
        RefreshScheduler.refreshNow(context)
        LiveService.sync(context)
        Widgets.renderAll(context, refreshing = true)
        status = savedText
    }

    fun test() {
        busy = true
        result = null
        showRaw = false
        scope.launch {
            try {
                val repo = StationRepository(context)
                result = withTimeoutOrNull(45_000) {
                    if (source == DataSourceKind.APP_ACCOUNT) {
                        // A test must not reuse a session cached for other credentials.
                        repo.fetchApp(account(), forceLogin = true, persistSession = false)
                    } else {
                        repo.fetchDeveloper(developer())
                    }
                } ?: FetchResult.Failed("timeout")
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tab_connection), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))

        Segmented(
            options = listOf(
                DataSourceKind.APP_ACCOUNT to stringResource(R.string.source_account),
                DataSourceKind.DEVELOPER_API to stringResource(R.string.source_developer),
            ),
            selected = source,
            onSelect = { source = it; result = null },
            modifier = Modifier.fillMaxWidth(),
        )

        EcoCard {
            if (source == DataSourceKind.APP_ACCOUNT) {
                Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.account_desc), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(14.dp))
                EcoField(email, { email = it }, stringResource(R.string.label_email), keyboardType = KeyboardType.Email)
                Spacer(Modifier.height(8.dp))
                EcoField(password, { password = it }, stringResource(R.string.label_password), secret = true)
                Spacer(Modifier.height(8.dp))
                EcoField(accSn, { accSn = it.uppercase().trim() }, stringResource(R.string.label_sn))
                Text(stringResource(R.string.sn_hint), style = MaterialTheme.typography.bodySmall, color = eco.muted, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(stringResource(if (showAdvanced) R.string.advanced_hide else R.string.advanced_show))
                }
                AnimatedVisibility(showAdvanced) {
                    EcoField(host, { host = it.trim() }, stringResource(R.string.label_host))
                }
            } else {
                Text(stringResource(R.string.dev_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.dev_desc), style = MaterialTheme.typography.bodySmall, color = eco.muted)
                Spacer(Modifier.height(14.dp))
                Segmented(
                    options = ApiRegion.entries.map {
                        it to stringResource(
                            when (it) {
                                ApiRegion.EUROPE -> R.string.region_europe
                                ApiRegion.AMERICAS -> R.string.region_americas
                                ApiRegion.GLOBAL -> R.string.region_global
                            },
                        )
                    },
                    selected = region,
                    onSelect = { region = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                EcoField(accessKey, { accessKey = it }, stringResource(R.string.label_access_key))
                Spacer(Modifier.height(8.dp))
                EcoField(secretKey, { secretKey = it }, stringResource(R.string.label_secret_key), secret = true)
                Spacer(Modifier.height(8.dp))
                EcoField(devSn, { devSn = it.uppercase().trim() }, stringResource(R.string.label_sn))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    enabled = !busy && accessKey.isNotBlank() && secretKey.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                devices = EcoFlowOpenApi(developer().copy(serialNumber = "-")).deviceList()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = e.message ?: e.javaClass.simpleName
                            } finally {
                                busy = false
                            }
                        }
                    },
                    shape = CircleShape,
                ) { Text(stringResource(R.string.action_find_devices)) }
                devices?.let { list ->
                    if (list.isEmpty()) Text(stringResource(R.string.devices_none), style = MaterialTheme.typography.bodySmall)
                    list.forEach { d ->
                        Text(
                            "${d.name} · ${d.productName ?: ""} · ${d.sn}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                devSn = d.sn
                                StationRepository(context).rememberModel(d.productName)
                                detectedModel = settings.detectedModelId
                            }.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.action_save), ::save, Modifier.weight(1f), enabled = !busy)
                OutlinedButton(
                    onClick = ::test,
                    enabled = !busy && complete,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text(stringResource(if (busy) R.string.testing else R.string.action_test)) }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = eco.muted)
            }
        }

        // ---- station model: decides which ports exist
        SectionLabel(stringResource(R.string.model_section))
        EcoCard {
            Text(stringResource(R.string.model_desc), style = MaterialTheme.typography.bodySmall, color = eco.muted)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = modelId == null,
                    onClick = { modelId = null; settings.stationModelId = null },
                    label = { Text(stringResource(R.string.model_auto)) },
                )
                StationModels.ALL.forEach { m ->
                    FilterChip(
                        selected = modelId == m.id,
                        onClick = { modelId = m.id; settings.stationModelId = m.id },
                        label = { Text(m.name) },
                    )
                }
            }
            if (modelId == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    StationModels.byId(detectedModel)?.let { stringResource(R.string.model_detected, it.name) }
                        ?: stringResource(R.string.model_not_detected),
                    style = MaterialTheme.typography.bodySmall,
                    color = eco.muted,
                )
            }
        }

        result?.let { r ->
            EcoCard(highlighted = r is FetchResult.Ok) {
                when (r) {
                    is FetchResult.Failed -> {
                        Text(stringResource(R.string.test_failed_title), style = MaterialTheme.typography.titleMedium, color = eco.bad)
                        Text(r.message, style = MaterialTheme.typography.bodySmall, color = eco.muted)
                    }
                    is FetchResult.Ok -> {
                        val s = r.snapshot
                        fun w(v: Int?) = v?.let { Format.watts(context, it) } ?: "—"
                        Text(stringResource(R.string.test_ok_title), style = MaterialTheme.typography.titleMedium, color = eco.good)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.test_battery, s.socPercent?.let { "$it%" } ?: "—"))
                        Text(stringResource(R.string.test_ac, w(s.acOutW)))
                        Text(stringResource(R.string.test_dc, w(s.dcOutW)))
                        Text(stringResource(R.string.test_in, w(s.totalInW)))
                        Text(stringResource(R.string.test_keys, r.rawKeys.size), color = eco.muted)
                        r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = eco.muted) }
                        TextButton(onClick = { showRaw = !showRaw }) {
                            Text(stringResource(if (showRaw) R.string.hide_keys else R.string.show_keys))
                        }
                        if (showRaw) {
                            val text = remember(r) {
                                r.rawKeys.entries.sortedBy { it.key }.take(500).joinToString("\n") { (k, v) -> "$k = $v" }
                            }
                            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        SectionLabel(stringResource(R.string.about_section))
        EcoCard {
            Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall, color = eco.muted)
        }
        Spacer(Modifier.height(12.dp))
    }
}
