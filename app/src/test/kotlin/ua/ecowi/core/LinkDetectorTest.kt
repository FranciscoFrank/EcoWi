package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDetectorTest {
    private val min = 60_000L

    private fun run(vararg checks: LinkCheck, from: LinkState = LinkState()): Pair<LinkState, List<LinkEvent>> {
        var s = from
        val events = ArrayList<LinkEvent>()
        for (c in checks) {
            val (next, e) = LinkDetector.process(s, c)
            s = next
            e?.let { events += it }
        }
        return s to events
    }

    @Test
    fun oneSilentCheckIsNotEnough() {
        val (s, events) = run(LinkCheck.Reached(0, 93), LinkCheck.Silent(15 * min))
        assertTrue(events.isEmpty())
        assertFalse(s.down)
    }

    @Test
    fun twoSilentChecksAfterThreeMinutesAlertOnce() {
        val (s, events) = run(
            LinkCheck.Reached(0, 93),
            LinkCheck.Silent(2 * min),
            LinkCheck.Silent(4 * min),
            LinkCheck.Silent(6 * min),
        )
        assertEquals(listOf(LinkEvent.Unreachable(0, 93, cloudSaysOffline = false)), events)
        assertTrue(s.down)
    }

    @Test
    fun quickSilentChecksWaitForTheMinimumSilence() {
        // live mode probes often; two silent probes within 3 minutes of the last data don't alert yet
        val (_, early) = run(LinkCheck.Reached(0, 50), LinkCheck.Silent(min), LinkCheck.Silent(2 * min))
        assertTrue(early.isEmpty())
        val (_, later) = run(LinkCheck.Reached(0, 50), LinkCheck.Silent(min), LinkCheck.Silent(2 * min), LinkCheck.Silent(3 * min))
        assertEquals(1, later.size)
    }

    @Test
    fun cloudOfflineFlagAlertsAtTheFirstCheck() {
        val (_, events) = run(LinkCheck.Reached(0, 40), LinkCheck.Silent(5 * min, cloudSaysOffline = true))
        assertEquals(listOf(LinkEvent.Unreachable(0, 40, cloudSaysOffline = true)), events)
    }

    @Test
    fun phoneOfflineNeverCounts() {
        val (s, events) = run(
            LinkCheck.Reached(0, 70),
            LinkCheck.Inconclusive, LinkCheck.Inconclusive, LinkCheck.Inconclusive,
        )
        assertTrue(events.isEmpty())
        assertEquals(0, s.silentChecks)
    }

    @Test
    fun backOnlineAfterAnAlertOnly() {
        val (s, events) = run(
            LinkCheck.Reached(0, 80),
            LinkCheck.Silent(10 * min), LinkCheck.Silent(20 * min),
            LinkCheck.Reached(30 * min, 79),
        )
        assertEquals(2, events.size)
        assertEquals(LinkEvent.BackOnline(79, 30 * min), events[1])
        assertFalse(s.down)
        assertEquals(0, s.silentChecks)

        // data after a single silent check: no "back online" noise
        val (_, quiet) = run(LinkCheck.Reached(0, 80), LinkCheck.Silent(10 * min), LinkCheck.Reached(11 * min, 80))
        assertTrue(quiet.isEmpty())
    }

    @Test
    fun neverReachedStationStillAlerts() {
        val (_, events) = run(LinkCheck.Silent(0), LinkCheck.Silent(min))
        val e = events.single() as LinkEvent.Unreachable
        assertNull(e.lastSeenMs)
        assertNull(e.lastSoc)
    }
}
