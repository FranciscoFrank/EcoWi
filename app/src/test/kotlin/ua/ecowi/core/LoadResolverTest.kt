package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadResolverTest {

    @Test
    fun idleWhenNothingDraws() {
        assertEquals(LoadView.Idle, LoadResolver.resolve(0, 1, DisplayMode.CYCLE))
        assertEquals(LoadView.Idle, LoadResolver.resolve(null, null, DisplayMode.PREFER_AC))
    }

    @Test
    fun singleActiveChannelIsShownInAnyMode() {
        for (mode in DisplayMode.entries) {
            assertEquals(LoadView.Single(LoadChannel.DC, 15), LoadResolver.resolve(0, 15, mode))
            assertEquals(LoadView.Single(LoadChannel.AC, 300), LoadResolver.resolve(300, null, mode))
        }
    }

    @Test
    fun preferenceWinsWhenBothActive() {
        assertEquals(LoadView.Single(LoadChannel.AC, 300), LoadResolver.resolve(300, 15, DisplayMode.PREFER_AC))
        assertEquals(LoadView.Single(LoadChannel.DC, 15), LoadResolver.resolve(300, 15, DisplayMode.PREFER_DC))
    }

    @Test
    fun cycleAlternatesWhenBothActive() {
        assertEquals(LoadView.Alternating(300, 15), LoadResolver.resolve(300, 15, DisplayMode.CYCLE))
    }

    @Test
    fun batteryBands() {
        assertEquals(BatteryBand.CRITICAL, BatteryBand.of(7))
        assertEquals(BatteryBand.LOW, BatteryBand.of(25))
        assertEquals(BatteryBand.MEDIUM, BatteryBand.of(50))
        assertEquals(BatteryBand.HIGH, BatteryBand.of(51))
    }

    @Test
    fun staleness() {
        val min = 60_000L
        assertFalse(Freshness.isStale(0, 30 * min, refreshIntervalMin = 15))
        assertTrue(Freshness.isStale(0, 36 * min, refreshIntervalMin = 15))
        assertFalse(Freshness.isStale(0, 100 * min, refreshIntervalMin = 60))
    }
}
