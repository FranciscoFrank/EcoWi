package ua.ecowi.core

/** Something worth a push notification. */
sealed interface StationEvent {
    data class BatteryLow(val soc: Int, val threshold: Int) : StationEvent
    data class BatteryFull(val soc: Int) : StationEvent
    data class ChargingStarted(val inputW: Int?) : StationEvent
    /** AC input appeared. [charging] = the station also started charging from it. */
    data class GridRestored(val charging: Boolean) : StationEvent
    data object GridLost : StationEvent
    data class PortConnected(val port: Port, val watts: Int) : StationEvent
}

/** Which events the user wants. Thresholds are fixed to what was asked: 15 % and 5 %. */
data class EventConfig(
    val lowThresholds: List<Int> = listOf(15, 5),
    val notifyLow: Boolean = true,
    val notifyFull: Boolean = true,
    val notifyChargingStarted: Boolean = true,
    val notifyGrid: Boolean = true,
    val notifyPorts: Boolean = true,
)

/**
 * Memory between observations. Persisted by the app (see EventStateStore) so that
 * notifications survive process death and don't repeat.
 */
data class DetectorState(
    val seeded: Boolean = false,
    /** Low thresholds that have already fired and wait for the battery to recover. */
    val firedLow: Set<Int> = emptySet(),
    val fullFired: Boolean = false,
    val charging: Boolean? = null,
    val lastChargingStartMs: Long = 0L,
    val grid: Boolean? = null,
    /** Ports currently drawing power → since when. */
    val activePorts: Map<Port, Long> = emptyMap(),
    /** Ports that went idle → since when (debounce before a new "connected"). */
    val idlePorts: Map<Port, Long> = emptyMap(),
)

/**
 * Turns a stream of snapshots into events with hysteresis, so a battery hovering at 15 %
 * or a phone trickle-charging at 1–3 W doesn't spam:
 *  - low N %: fires when SOC ≤ N while not charging; re-arms at N + 5 %;
 *  - full: fires when SOC reaches the charge limit (100 % or the app's limit); re-arms 5 % below;
 *  - charging started: idle/discharging → charging, at most once per 10 min;
 *  - grid: AC-input flag flips (needs the app-account source — Open API rarely sends it);
 *  - port connected: a port goes above its threshold after ≥ 2 min idle.
 * The very first observation only seeds the state: installing the app doesn't trigger
 * "battery full" just because the station happens to be full.
 */
object EventDetector {

    const val REARM_MARGIN = 5
    const val CHARGING_COOLDOWN_MS = 10 * 60_000L
    const val PORT_IDLE_DEBOUNCE_MS = 2 * 60_000L
    const val USB_ACTIVE_W = 3
    const val AC_ACTIVE_W = 10
    private const val LONG_AGO = -1L

    fun threshold(port: Port): Int = if (port.isAc) AC_ACTIVE_W else USB_ACTIVE_W

    fun process(
        state: DetectorState,
        snap: PowerSnapshot,
        config: EventConfig = EventConfig(),
    ): Pair<DetectorState, List<StationEvent>> {
        val now = snap.fetchedAtMs
        val soc = snap.socPercent
        val charging = snap.isCharging
        val events = ArrayList<StationEvent>()

        // ---- ports (state always tracked, events only if seeded)
        val active = HashMap(state.activePorts)
        val idle = HashMap(state.idlePorts)
        val newlyActive = ArrayList<Pair<Port, Int>>()
        for ((port, watts) in snap.ports) {
            val isActive = watts >= threshold(port)
            if (isActive && port !in active) {
                val idleSince = idle.remove(port)
                active[port] = now
                val debounced = idleSince == null || idleSince == LONG_AGO || now - idleSince >= PORT_IDLE_DEBOUNCE_MS
                if (debounced) newlyActive += port to watts
            } else if (!isActive && port in active) {
                active.remove(port)
                idle[port] = now
            } else if (!isActive && port !in idle) {
                // Seen idle for the first time: treat as "idle for long" so a plug-in right
                // after app start still notifies.
                idle[port] = if (state.seeded) now else LONG_AGO
            }
        }

        if (!state.seeded) {
            val firedLow = if (soc == null) emptySet()
            else config.lowThresholds.filter { soc <= it }.toSet() // already below: don't nag
            val seeded = DetectorState(
                seeded = soc != null,
                firedLow = firedLow,
                fullFired = soc != null && soc >= snap.fullAtPercent,
                charging = charging,
                grid = snap.gridConnected,
                activePorts = active,
                idlePorts = idle,
            )
            return seeded to emptyList()
        }

        // ---- battery low
        var firedLow = state.firedLow
        if (soc != null) {
            firedLow = firedLow.filter { soc < it + REARM_MARGIN }.toSet()
            if (!charging) {
                // Report only the lowest crossed threshold (15 → 5 jump = one "5 %" push).
                val crossed = config.lowThresholds.filter { soc <= it && it !in firedLow }
                if (crossed.isNotEmpty()) {
                    if (config.notifyLow) events += StationEvent.BatteryLow(soc, crossed.min())
                    firedLow = firedLow + config.lowThresholds.filter { soc <= it }
                }
            }
        }

        // ---- battery full
        var fullFired = state.fullFired
        if (soc != null) {
            val target = snap.fullAtPercent
            if (fullFired && soc <= target - REARM_MARGIN) fullFired = false
            if (!fullFired && soc >= target) {
                if (config.notifyFull) events += StationEvent.BatteryFull(soc)
                fullFired = true
            }
        }

        // ---- grid
        val grid = snap.gridConnected ?: state.grid
        var gridRestored = false
        if (snap.gridConnected != null && state.grid != null && snap.gridConnected != state.grid) {
            if (snap.gridConnected) gridRestored = true
            else if (config.notifyGrid) events += StationEvent.GridLost
        }

        // ---- charging start
        var lastStart = state.lastChargingStartMs
        val startedCharging = state.charging == false && charging
        if (startedCharging && (lastStart == 0L || now - lastStart >= CHARGING_COOLDOWN_MS)) lastStart = now
        val chargingEventDue = startedCharging && lastStart == now

        if (gridRestored && config.notifyGrid) {
            events += StationEvent.GridRestored(charging = chargingEventDue || charging)
        } else if (chargingEventDue && config.notifyChargingStarted) {
            events += StationEvent.ChargingStarted(snap.totalInW)
        }

        // ---- ports
        if (config.notifyPorts) newlyActive.forEach { (p, w) -> events += StationEvent.PortConnected(p, w) }

        return DetectorState(
            seeded = true,
            firedLow = firedLow,
            fullFired = fullFired,
            charging = charging,
            lastChargingStartMs = lastStart,
            grid = grid,
            activePorts = active,
            idlePorts = idle,
        ) to events
    }
}
