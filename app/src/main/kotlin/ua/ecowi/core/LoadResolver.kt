package ua.ecowi.core

enum class LoadChannel { AC, DC }

/**
 * How the widget presents consumption:
 *  - [PREFER_AC] / [PREFER_DC]: show the preferred channel; if it is idle but the other one
 *    is drawing power, show the other one (so real consumption is never hidden).
 *  - [CYCLE]: when both channels are active, alternate between them every N seconds.
 */
enum class DisplayMode { PREFER_AC, PREFER_DC, CYCLE }

sealed interface LoadView {
    /** No measurable consumption (or no data). */
    data object Idle : LoadView

    data class Single(val channel: LoadChannel, val watts: Int) : LoadView

    /** Both channels active and the user chose [DisplayMode.CYCLE]. */
    data class Alternating(val acWatts: Int, val dcWatts: Int) : LoadView
}

object LoadResolver {

    /** Readings below this are treated as idle (USB ports often report 0–1 W of noise). */
    const val ACTIVE_THRESHOLD_W = 2

    fun resolve(acW: Int?, dcW: Int?, mode: DisplayMode): LoadView {
        val ac = acW?.takeIf { it >= ACTIVE_THRESHOLD_W }
        val dc = dcW?.takeIf { it >= ACTIVE_THRESHOLD_W }

        return when {
            ac == null && dc == null -> LoadView.Idle
            ac != null && dc == null -> LoadView.Single(LoadChannel.AC, ac)
            ac == null && dc != null -> LoadView.Single(LoadChannel.DC, dc)
            else -> when (mode) {
                DisplayMode.PREFER_AC -> LoadView.Single(LoadChannel.AC, ac!!)
                DisplayMode.PREFER_DC -> LoadView.Single(LoadChannel.DC, dc!!)
                DisplayMode.CYCLE -> LoadView.Alternating(ac!!, dc!!)
            }
        }
    }
}

enum class BatteryBand { CRITICAL, LOW, MEDIUM, HIGH;
    companion object {
        fun of(soc: Int?): BatteryBand? = when {
            soc == null -> null
            soc <= 10 -> CRITICAL
            soc <= 25 -> LOW
            soc <= 50 -> MEDIUM
            else -> HIGH
        }
    }
}

object Freshness {
    /** Data older than this is shown as stale (dimmed + warning). */
    fun staleAfterMs(refreshIntervalMin: Int): Long = maxOf(2L * refreshIntervalMin, 35L) * 60_000L

    fun isStale(fetchedAtMs: Long, nowMs: Long, refreshIntervalMin: Int): Boolean =
        nowMs - fetchedAtMs > staleAfterMs(refreshIntervalMin)
}
