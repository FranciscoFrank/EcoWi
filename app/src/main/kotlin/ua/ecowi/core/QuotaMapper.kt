package ua.ecowi.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Maps a raw EcoFlow quota payload (REST `quota/all` `data`, or MQTT `params`) to [PowerSnapshot].
 *
 * Key names differ between transports and generations, so matching is tolerant:
 *  - case-insensitive, underscores ignored: `cmsBattSoc` == `cms_batt_soc`;
 *  - "leaf" candidates match the part after the last dot (`20_1.powGetAcOut` -> `powgetacout`);
 *  - candidates containing a dot match the whole normalised key (legacy `pd.soc`, `inv.outputWatts`).
 *
 * DELTA 3 family reports some outputs as negative numbers, so output powers use abs().
 */
object QuotaMapper {

    /** Above this many minutes EcoFlow sends placeholders (5999 / 15999 = "unknown"). */
    const val REMAIN_SENTINEL_MIN = 5999

    private val SOC = listOf("cmsbattsoc", "bmsbattsoc", "pd.soc", "bmsmaster.soc", "soc")
    private val AC_OUT = listOf("powgetacout", "inv.outputwatts")
    private val TOTAL_IN = listOf("powinsumw", "pd.wattsinsum")
    private val TOTAL_OUT = listOf("powoutsumw", "pd.wattsoutsum")
    private val DISCHARGE_REMAIN = listOf("cmsdsgremtime", "bmsdsgremtime")
    private val CHARGE_REMAIN = listOf("cmschgremtime", "bmschgremtime")

    /** DELTA 3 generation DC outputs (12 V car socket, USB-A, USB-C). */
    private val DC_PORTS = listOf(
        "powget12v", "powgettypec1", "powgettypec2", "powgettypec3", "powgetqcusb1", "powgetqcusb2",
    )

    /** Per-port candidates (DELTA 3 names first, DELTA 2 names second). */
    private val PORT_KEYS: Map<Port, List<String>> = mapOf(
        Port.AC to AC_OUT,
        Port.USB_A1 to listOf("powgetqcusb1", "pd.qcusb1watts", "pd.usb1watts"),
        Port.USB_A2 to listOf("powgetqcusb2", "pd.qcusb2watts", "pd.usb2watts"),
        Port.USB_C1 to listOf("powgettypec1", "pd.typec1watts"),
        Port.USB_C2 to listOf("powgettypec2", "pd.typec2watts"),
        Port.DC_12V to listOf("powget12v", "pd.carwatts"),
    )

    private val GRID_FLAG = listOf("plugininfoacinflag")
    private val AC_IN = listOf("powgetacin", "inv.inputwatts")
    private val SOLAR_IN = listOf("powgetpv", "mppt.inwatts")
    private val CHARGE_STATE = listOf("cmschgdsgstate", "bmschgdsgstate")
    private val CHARGE_LIMIT = listOf("cmsmaxchgsoc", "ems.maxchargesoc")

    /** DELTA 2 / older generation DC outputs. */
    private val DC_PORTS_LEGACY = listOf(
        "pd.usb1watts", "pd.usb2watts", "pd.qcusb1watts", "pd.qcusb2watts",
        "pd.typec1watts", "pd.typec2watts", "pd.carwatts",
    )

    fun map(raw: Map<String, Any?>, nowMs: Long): PowerSnapshot {
        val index = KeyIndex(flatten(raw))

        val soc = index.number(SOC)?.roundToInt()?.takeIf { it in 0..100 }
        val acOut = index.number(AC_OUT)?.let { abs(it).roundToInt() }
        val totalIn = index.number(TOTAL_IN)?.let { abs(it).roundToInt() }
        val totalOut = index.number(TOTAL_OUT)?.let { abs(it).roundToInt() }

        val dcPorts = index.sumOf(DC_PORTS) ?: index.sumOf(DC_PORTS_LEGACY)
        val dcOut = dcPorts ?: if (totalOut != null && acOut != null) maxOf(0, totalOut - acOut) else null

        val ports = PORT_KEYS.mapNotNull { (port, keys) ->
            index.number(keys)?.let { port to abs(it).roundToInt() }
        }.toMap()

        val chargeState = when (index.number(CHARGE_STATE)?.roundToInt()) {
            0 -> ChargeState.IDLE
            1 -> ChargeState.DISCHARGING
            2 -> ChargeState.CHARGING
            else -> null
        }

        return PowerSnapshot(
            socPercent = soc,
            acOutW = acOut,
            dcOutW = dcOut,
            totalInW = totalIn,
            totalOutW = totalOut,
            dischargeRemainMin = index.number(DISCHARGE_REMAIN)?.roundToInt()?.takeIf(::isRealRemain),
            chargeRemainMin = index.number(CHARGE_REMAIN)?.roundToInt()?.takeIf(::isRealRemain),
            fetchedAtMs = nowMs,
            ports = ports,
            gridConnected = index.number(GRID_FLAG)?.let { it >= 1.0 },
            acInW = index.number(AC_IN)?.let { abs(it).roundToInt() },
            solarInW = index.number(SOLAR_IN)?.let { abs(it).roundToInt() },
            chargeState = chargeState,
            chargeLimitPercent = index.number(CHARGE_LIMIT)?.roundToInt()?.takeIf { it in 1..100 },
        )
    }

    /** Flattens nested maps into dotted keys. Lists are ignored (not needed for these metrics). */
    fun flatten(raw: Map<String, Any?>): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        fun walk(prefix: String, value: Any?) {
            if (value is Map<*, *>) {
                value.forEach { (k, v) -> walk(if (prefix.isEmpty()) k.toString() else "$prefix.$k", v) }
            } else if (value !is List<*>) {
                out[prefix] = value
            }
        }
        raw.forEach { (k, v) -> walk(k, v) }
        return out
    }

    fun normalise(key: String): String = key.lowercase().replace("_", "")

    private fun isRealRemain(min: Int): Boolean = min in 1 until REMAIN_SENTINEL_MIN

    private class KeyIndex(flat: Map<String, Any?>) {
        private val byFull = HashMap<String, Double>()
        private val byLeaf = HashMap<String, Double>()

        init {
            flat.forEach { (key, value) ->
                val number = toDouble(value) ?: return@forEach
                val full = normalise(key)
                byFull.putIfAbsent(full, number)
                // Prefer the un-prefixed key if both `x` and `20_1.x` are present.
                val leaf = full.substringAfterLast('.')
                if (!full.contains('.')) byLeaf[leaf] = number else byLeaf.putIfAbsent(leaf, number)
            }
        }

        fun number(candidates: List<String>): Double? =
            candidates.firstNotNullOfOrNull { lookup(it) }

        /** Sum of abs() of the candidates that exist; null when none of them is present. */
        fun sumOf(candidates: List<String>): Int? {
            val present = candidates.mapNotNull { lookup(it) }
            return if (present.isEmpty()) null else present.sumOf { abs(it) }.roundToInt()
        }

        private fun lookup(candidate: String): Double? =
            if (candidate.contains('.')) byFull[candidate] else byLeaf[candidate]

        private fun toDouble(v: Any?): Double? = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            is Boolean -> null
            else -> null
        }?.takeIf { it.isFinite() }
    }
}
