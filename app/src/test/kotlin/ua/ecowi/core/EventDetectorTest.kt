package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {

    private val min = 60_000L

    private fun snap(
        t: Long,
        soc: Int,
        state: ChargeState = ChargeState.DISCHARGING,
        grid: Boolean? = null,
        inW: Int = 0,
        ports: Map<Port, Int> = emptyMap(),
        limit: Int? = null,
    ) = PowerSnapshot(
        socPercent = soc, acOutW = ports[Port.AC], dcOutW = null, totalInW = inW, totalOutW = null,
        dischargeRemainMin = null, chargeRemainMin = null, fetchedAtMs = t,
        ports = ports, gridConnected = grid, chargeState = state, chargeLimitPercent = limit,
    )

    /** Feeds snapshots in order and returns every event emitted after the seeding one. */
    private fun run(vararg snaps: PowerSnapshot): List<StationEvent> {
        var state = DetectorState()
        val all = ArrayList<StationEvent>()
        for (s in snaps) {
            val (next, events) = EventDetector.process(state, s)
            state = next
            all += events
        }
        return all
    }

    @Test
    fun firstObservationOnlySeeds() {
        assertTrue(run(snap(0, 100, ChargeState.IDLE)).isEmpty())
        assertTrue(run(snap(0, 4)).isEmpty())
    }

    @Test
    fun lowThresholdsFireOnceWithHysteresis() {
        val e = run(
            snap(0, 30), snap(1 * min, 15), snap(2 * min, 16), snap(3 * min, 15), // hover: one push
            snap(4 * min, 5), snap(5 * min, 4),
        )
        assertEquals(listOf(StationEvent.BatteryLow(15, 15), StationEvent.BatteryLow(5, 5)), e)
    }

    @Test
    fun jumpPastBothThresholdsGivesOnePush() {
        assertEquals(listOf(StationEvent.BatteryLow(4, 5)), run(snap(0, 20), snap(1 * min, 4), snap(2 * min, 12)))
    }

    @Test
    fun lowRearmsAfterRecovery() {
        val e = run(
            snap(0, 30), snap(1 * min, 15),
            snap(2 * min, 21, ChargeState.CHARGING), snap(3 * min, 15),
        ).filterIsInstance<StationEvent.BatteryLow>()
        assertEquals(2, e.size)
    }

    @Test
    fun installedAtLowSocDoesNotNagButStillWarnsAtFive() {
        assertEquals(listOf(StationEvent.BatteryLow(5, 5)), run(snap(0, 13), snap(1 * min, 14), snap(2 * min, 5)))
    }

    @Test
    fun fullRespectsChargeLimit() {
        val e = run(
            snap(0, 80, ChargeState.CHARGING, limit = 90),
            snap(1 * min, 90, ChargeState.IDLE, limit = 90),
            snap(2 * min, 89, ChargeState.IDLE, limit = 90),
            snap(3 * min, 90, ChargeState.IDLE, limit = 90),
        )
        assertEquals(listOf(StationEvent.BatteryFull(90)), e)
    }

    @Test
    fun chargingStartWithCooldown() {
        val e = run(
            snap(0, 50), snap(1 * min, 50, ChargeState.CHARGING, inW = 800),
            snap(2 * min, 51), snap(3 * min, 51, ChargeState.CHARGING), // within 10 min: silent
            snap(20 * min, 52), snap(21 * min, 52, ChargeState.CHARGING),
        )
        assertEquals(listOf(StationEvent.ChargingStarted(800), StationEvent.ChargingStarted(0)), e)
    }

    @Test
    fun gridRestoredMergesWithChargingStart() {
        val e = run(
            snap(0, 50, grid = true, state = ChargeState.IDLE),
            snap(1 * min, 49, grid = false),
            snap(2 * min, 49, grid = true, state = ChargeState.CHARGING, inW = 900),
        )
        assertEquals(listOf(StationEvent.GridLost, StationEvent.GridRestored(charging = true)), e)
    }

    @Test
    fun portConnectedIsDebounced() {
        val e = run(
            snap(0, 70, ports = mapOf(Port.USB_C1 to 0, Port.AC to 0)),
            snap(10_000, 70, ports = mapOf(Port.USB_C1 to 18, Port.AC to 0)), // plugged right after start
            snap(20_000, 70, ports = mapOf(Port.USB_C1 to 1, Port.AC to 0)), // trickle dip
            snap(40_000, 70, ports = mapOf(Port.USB_C1 to 15, Port.AC to 5)), // back within 2 min; AC below 10 W
            snap(10 * min, 70, ports = mapOf(Port.USB_C1 to 0, Port.AC to 400)),
        )
        assertEquals(listOf(StationEvent.PortConnected(Port.USB_C1, 18), StationEvent.PortConnected(Port.AC, 400)), e)
    }

    @Test
    fun disabledCategoriesStaySilent() {
        val cfg = EventConfig(notifyLow = false, notifyPorts = false)
        val st = EventDetector.process(DetectorState(), snap(0, 30)).first
        val (_, ev) = EventDetector.process(st, snap(1 * min, 10, ports = mapOf(Port.USB_A1 to 10)), cfg)
        assertTrue(ev.isEmpty())
    }
}
