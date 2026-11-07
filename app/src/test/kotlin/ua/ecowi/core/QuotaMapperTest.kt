package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotaMapperTest {

    @Test
    fun mapsDelta3StylePayload() {
        // Shape of a DELTA 3 quota payload: camelCase keys, negative output powers.
        val raw = mapOf<String, Any?>(
            "cmsBattSoc" to 87.4,
            "bmsBattSoc" to 86,
            "powInSumW" to 0,
            "powOutSumW" to 262,
            "powGetAcOut" to -245,
            "powGetQcusb1" to -5,
            "powGetQcusb2" to 0,
            "powGetTypec1" to -12,
            "powGet_12v" to 0,
            "cmsDsgRemTime" to 205,
            "cmsChgRemTime" to 5999,
        )
        val s = QuotaMapper.map(raw, nowMs = 1000L)
        assertEquals(87, s.socPercent)
        assertEquals(245, s.acOutW)
        assertEquals(17, s.dcOutW)
        assertEquals(262, s.totalOutW)
        assertEquals(205, s.dischargeRemainMin)
        assertNull(s.chargeRemainMin) // 5999 is EcoFlow's "unknown" placeholder
        assertEquals(1000L, s.fetchedAtMs)
    }

    @Test
    fun acceptsSnakeCaseAndPrefixedKeysAndStrings() {
        val raw = mapOf<String, Any?>(
            "20_1.cms_batt_soc" to "42",
            "20_1.pow_get_ac_out" to "-100",
            "20_1.pow_get_typec1" to "-30",
        )
        val s = QuotaMapper.map(raw, 0L)
        assertEquals(42, s.socPercent)
        assertEquals(100, s.acOutW)
        assertEquals(30, s.dcOutW)
    }

    @Test
    fun mapsNestedLegacyDelta2Payload() {
        val raw = mapOf<String, Any?>(
            "pd" to mapOf("soc" to 64, "usb1Watts" to 3, "typec1Watts" to 20, "carWatts" to 0, "wattsInSum" to 120),
            "inv" to mapOf("outputWatts" to 300),
        )
        val s = QuotaMapper.map(raw, 0L)
        assertEquals(64, s.socPercent)
        assertEquals(300, s.acOutW)
        assertEquals(23, s.dcOutW)
        assertEquals(120, s.totalInW)
    }

    @Test
    fun derivesDcFromTotalsWhenNoPortKeys() {
        val raw = mapOf<String, Any?>("cmsBattSoc" to 50, "powOutSumW" to 80, "powGetAcOut" to -60)
        assertEquals(20, QuotaMapper.map(raw, 0L).dcOutW)
    }

    @Test
    fun unknownStaysNull() {
        val s = QuotaMapper.map(mapOf("somethingElse" to 1), 0L)
        assertNull(s.socPercent)
        assertNull(s.acOutW)
        assertNull(s.dcOutW)
    }

    @Test
    fun incrementalUpdateKeepsRecentValues() {
        val old = QuotaMapper.map(mapOf("cmsBattSoc" to 80, "powGetAcOut" to -200, "powGetQcusb1" to -5), 0L)
        val partial = QuotaMapper.map(mapOf("cmsBattSoc" to 79), 60_000L).fillFrom(old)
        assertEquals(79, partial.socPercent)
        assertEquals(200, partial.acOutW)
        assertEquals(5, partial.dcOutW)

        val tooOld = QuotaMapper.map(mapOf("cmsBattSoc" to 79), 7 * 60 * 60_000L).fillFrom(old)
        assertNull(tooOld.acOutW)
    }

    @Test
    fun rejectsOutOfRangeSoc() {
        assertNull(QuotaMapper.map(mapOf("cmsBattSoc" to 140), 0L).socPercent)
    }
}
