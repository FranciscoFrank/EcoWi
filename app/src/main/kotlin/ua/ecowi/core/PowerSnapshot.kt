package ua.ecowi.core

/** Output ports the station reports power for individually. AC is one combined reading. */
enum class Port(val isAc: Boolean = false) {
    AC(isAc = true), USB_A1, USB_A2, USB_C1, USB_C2, DC_12V;
}

enum class ChargeState { IDLE, DISCHARGING, CHARGING }

/**
 * Normalised station state. Every field is nullable: the cloud may omit keys,
 * and "unknown" must never be rendered as "0".
 */
data class PowerSnapshot(
    val socPercent: Int?,
    val acOutW: Int?,
    val dcOutW: Int?,
    val totalInW: Int?,
    val totalOutW: Int?,
    val dischargeRemainMin: Int?,
    val chargeRemainMin: Int?,
    val fetchedAtMs: Long,
    /** Per-port output power, only for ports present in the payload. */
    val ports: Map<Port, Int> = emptyMap(),
    /** AC input (grid / generator) plugged and live. Null = the payload doesn't say. */
    val gridConnected: Boolean? = null,
    val acInW: Int? = null,
    val solarInW: Int? = null,
    val chargeState: ChargeState? = null,
    /** Charge limit set in the EcoFlow app (e.g. 90). "Full" means reaching it. */
    val chargeLimitPercent: Int? = null,
) {
    val isCharging: Boolean
        get() = chargeState?.let { it == ChargeState.CHARGING }
            ?: ((totalInW ?: 0) >= LoadResolver.ACTIVE_THRESHOLD_W && (totalInW ?: 0) > (totalOutW ?: 0))

    val hasAnyData: Boolean get() = socPercent != null || acOutW != null || dcOutW != null

    /** SOC at which the station stops charging. */
    val fullAtPercent: Int get() = chargeLimitPercent?.takeIf { it in 50..100 } ?: 100

    /**
     * MQTT pushes are often incremental (only changed keys). Fill gaps from a recent previous
     * snapshot so a message carrying only `cmsBattSoc` doesn't blank the consumption values.
     * An unchanged value is still the current value, so this is correct while [older] is recent.
     */
    fun fillFrom(older: PowerSnapshot?, maxAgeMs: Long = 6 * 60 * 60_000L): PowerSnapshot {
        if (older == null || fetchedAtMs - older.fetchedAtMs > maxAgeMs) return this
        return copy(
            socPercent = socPercent ?: older.socPercent,
            acOutW = acOutW ?: older.acOutW,
            dcOutW = dcOutW ?: older.dcOutW,
            totalInW = totalInW ?: older.totalInW,
            totalOutW = totalOutW ?: older.totalOutW,
            dischargeRemainMin = dischargeRemainMin ?: older.dischargeRemainMin,
            chargeRemainMin = chargeRemainMin ?: older.chargeRemainMin,
            ports = older.ports + ports,
            gridConnected = gridConnected ?: older.gridConnected,
            acInW = acInW ?: older.acInW,
            solarInW = solarInW ?: older.solarInW,
            chargeState = chargeState ?: older.chargeState,
            chargeLimitPercent = chargeLimitPercent ?: older.chargeLimitPercent,
        )
    }
}
