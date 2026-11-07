package ua.ecowi.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import ua.ecowi.core.LinkState
import ua.ecowi.core.Port
import ua.ecowi.core.PortLayout
import ua.ecowi.core.Region
import ua.ecowi.core.StationModel
import ua.ecowi.data.AppSettings
import ua.ecowi.data.LinkStore
import ua.ecowi.data.CachedState
import ua.ecowi.data.SnapshotCache

/** What the screens need: last cached state + whether the app is configured / live. */
data class StationUi(
    val cached: CachedState,
    val configured: Boolean,
    val live: Boolean,
    /** Whether the station itself answers (see LinkDetector). */
    val link: LinkState = LinkState(),
    /** Chosen or detected model; null = unknown, ports are learned from use. */
    val model: StationModel? = null,
    val learnedPorts: Set<Port> = emptySet(),
    val region: Region = Region.V230,
) {
    /** Ports to show: the model's own plus any that has drawn power. */
    val ports: List<Port> get() = PortLayout.visible(model, learnedPorts)
}

fun loadStationUi(context: Context): StationUi {
    val settings = AppSettings(context)
    val cache = SnapshotCache(context)
    return StationUi(
        cached = cache.load(),
        configured = settings.isConfigured,
        live = settings.liveMode,
        link = LinkStore(context).load(),
        model = settings.stationModel,
        learnedPorts = cache.learnedPorts(),
        region = Region.forCountry(java.util.Locale.getDefault().country),
    )
}

/**
 * Polls the snapshot cache while the screen is visible. The live service and the background
 * worker write the cache; the UI just reads it — no extra connections from the UI.
 */
@Composable
fun rememberStationUi(tick: Int = 0): State<StationUi> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(loadStationUi(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, tick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.value = loadStationUi(context)
                delay(2_000)
            }
        }
    }
    return state
}
