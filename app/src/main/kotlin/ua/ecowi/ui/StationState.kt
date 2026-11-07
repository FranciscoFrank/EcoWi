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
import ua.ecowi.data.AppSettings
import ua.ecowi.data.CachedState
import ua.ecowi.data.SnapshotCache

/** What the screens need: last cached state + whether the app is configured / live. */
data class StationUi(
    val cached: CachedState,
    val configured: Boolean,
    val live: Boolean,
)

fun loadStationUi(context: Context): StationUi {
    val settings = AppSettings(context)
    return StationUi(SnapshotCache(context).load(), settings.isConfigured, settings.liveMode)
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
