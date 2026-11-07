package ua.ecowi.appwidget

import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import ua.ecowi.R
import ua.ecowi.core.BatteryBand
import ua.ecowi.core.Freshness
import ua.ecowi.core.LoadResolver
import ua.ecowi.core.LoadView
import ua.ecowi.data.AppSettings
import ua.ecowi.data.LinkStore
import ua.ecowi.data.SnapshotCache
import ua.ecowi.data.StationRepository
import ua.ecowi.data.WidgetAppearance
import java.util.Date

/** Everything a widget shows, independent of its layout. */
data class WidgetModel(
    val soc: Int?,
    val socText: String,
    val band: BatteryBand?,
    val charging: Boolean,
    val gridConnected: Boolean?,
    /** Fits a 1-row widget (~24 chars): remaining time + data time. */
    val captionShort: String,
    /** For bigger layouts: also the charging input power. */
    val captionFull: String,
    val load: LoadView,
    val hasData: Boolean,
    val cycleMs: Int,
    val needsSetup: Boolean,
    val appearance: WidgetAppearance,
    val phone: PhoneBattery,
)

data class PhoneBattery(val percent: Int, val charging: Boolean)

object WidgetModels {

    fun build(context: Context, widgetId: Int, refreshing: Boolean): WidgetModel {
        val settings = AppSettings(context)
        val state = SnapshotCache(context).load()
        val snap = state.snapshot
        val needsSetup = !settings.isConfigured
        val now = System.currentTimeMillis()

        val status: String? = when {
            needsSetup -> context.getString(R.string.caption_setup)
            refreshing -> context.getString(R.string.caption_refreshing)
            snap == null && state.lastError != null ->
                context.getString(R.string.caption_no_data, shortError(state.lastError))
            snap == null -> context.getString(R.string.caption_loading)
            else -> null
        }
        val link = LinkStore(context).load()
        val (captionShort, captionFull) = if (status != null || snap == null) {
            (status ?: "") to (status ?: "")
        } else run {
            val freshness = if (settings.liveMode) 10 else settings.refreshIntervalMin
            val stale = Freshness.isStale(snap.fetchedAtMs, now, freshness)
            val failedSince = state.lastError != null && state.lastAttemptMs > snap.fetchedAtMs
            val clock = DateFormat.getTimeFormat(context).format(Date(snap.fetchedAtMs))
            val time = if (stale || failedSince) "⚠ $clock" else clock
            if (link.down) {
                // The station stopped answering: say so instead of showing old numbers as current.
                val seen = DateFormat.getTimeFormat(context).format(Date(link.lastSeenMs ?: snap.fetchedAtMs))
                val text = context.getString(R.string.caption_unreachable, seen)
                return@run text to text
            }
            val remain = (if (snap.isCharging) snap.chargeRemainMin else snap.dischargeRemainMin)
                ?.let { Format.duration(context, it) }
            val input = if (snap.isCharging) {
                context.getString(R.string.caption_charging, Format.watts(context, snap.totalInW ?: 0))
            } else {
                null
            }
            listOfNotNull(remain ?: input, time).joinToString(" · ") to
                listOfNotNull(input, remain, time).joinToString(" · ")
        }

        val load = if (snap == null) LoadView.Idle
        else LoadResolver.resolve(snap.acOutW, snap.dcOutW, settings.displayMode(widgetId))

        return WidgetModel(
            soc = snap?.socPercent,
            socText = snap?.socPercent?.let { context.getString(R.string.soc_percent, it) }
                ?: context.getString(R.string.soc_unknown),
            band = BatteryBand.of(snap?.socPercent),
            charging = snap?.isCharging == true,
            gridConnected = snap?.gridConnected,
            captionShort = captionShort,
            captionFull = captionFull,
            load = load,
            hasData = snap != null,
            cycleMs = settings.cycleSeconds(widgetId) * 1000,
            needsSetup = needsSetup,
            appearance = settings.appearance(widgetId),
            phone = phoneBattery(context),
        )
    }

    /** This phone's own battery (for the "batteries" ring layout). */
    fun phoneBattery(context: Context): PhoneBattery {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 0
        return PhoneBattery(pct, bm?.isCharging == true)
    }

    /** Errors look like "REST: …; MQTT: …" — the last part is the final verdict. */
    private fun shortError(error: String): String = when {
        error == StationRepository.NO_CREDENTIALS -> "—"
        "MQTT:" in error -> error.substringAfterLast("MQTT:").trim()
        else -> error
    }.take(48)
}
