package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationModelsTest {

    @Test
    fun classicHasNoDcSectionAndOneUsbA() {
        val m = StationModels.DELTA_3_CLASSIC
        assertEquals(setOf(Port.AC, Port.USB_A1, Port.USB_C1, Port.USB_C2), m.ports)
        assertFalse(Port.DC_12V in m.ports)
        assertEquals(2, m.acOutlets[Region.V230])
        assertEquals(3, m.acOutlets[Region.V120])
    }

    @Test
    fun matchesCloudProductNames() {
        assertEquals(StationModels.DELTA_3_CLASSIC, StationModels.match("DELTA 3 Classic"))
        assertEquals(StationModels.DELTA_3_PLUS, StationModels.match("DELTA 3 Plus"))
        assertEquals(StationModels.DELTA_3_1500, StationModels.match("DELTA3-1500"))
        assertEquals(StationModels.DELTA_3_MAX_PLUS, StationModels.match("DELTA 3 Max Plus"))
        assertEquals(StationModels.DELTA_3, StationModels.match("DELTA 3"))
    }

    @Test
    fun longestAliasWinsInsideLongerNames() {
        assertEquals(StationModels.DELTA_3_PLUS, StationModels.match("EcoFlow DELTA 3 Plus (EU)"))
        assertEquals(StationModels.DELTA_3_CLASSIC, StationModels.match("My DELTA 3 Classic"))
    }

    @Test
    fun unknownNames() {
        assertNull(StationModels.match(null))
        assertNull(StationModels.match(""))
        assertNull(StationModels.match("RIVER 2"))
    }

    @Test
    fun manualChoiceBeatsDetection() {
        assertEquals(StationModels.DELTA_3_CLASSIC, StationModels.resolve("delta3_classic", "delta3_plus"))
        assertEquals(StationModels.DELTA_3_PLUS, StationModels.resolve(null, "delta3_plus"))
        assertNull(StationModels.resolve(null, null))
        assertEquals(StationModels.DELTA_3, StationModels.resolve("unknown_id", "delta3"))
    }

    @Test
    fun regionFromCountry() {
        assertEquals(Region.V120, Region.forCountry("US"))
        assertEquals(Region.V120, Region.forCountry("jp"))
        assertEquals(Region.V230, Region.forCountry("UA"))
        assertEquals(Region.V230, Region.forCountry(null))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(StationModels.ALL.size, StationModels.ALL.map { it.id }.toSet().size)
        assertTrue(StationModels.ALL.all { Port.AC in it.ports })
    }
}

class PortLayoutTest {

    private fun snap(ports: Map<Port, Int>) = PowerSnapshot(
        socPercent = 50, acOutW = 0, dcOutW = 0, totalInW = 0, totalOutW = 0,
        dischargeRemainMin = null, chargeRemainMin = null, fetchedAtMs = 0, ports = ports,
    )

    @Test
    fun knownModelHidesPortsItDoesNotHave() {
        // the Classic reports zeros for USB-A 2 and 12 V; they must not show up
        val visible = PortLayout.visible(StationModels.DELTA_3_CLASSIC, emptySet())
        assertEquals(listOf(Port.AC, Port.USB_A1, Port.USB_C1, Port.USB_C2), visible)
    }

    @Test
    fun portsLearnOnlyFromRealLoad() {
        val learned = PortLayout.learn(emptySet(), snap(mapOf(Port.USB_A2 to 0, Port.USB_C1 to 18, Port.DC_12V to 0)))
        assertEquals(setOf(Port.USB_C1), learned)
        assertEquals(setOf(Port.USB_C1, Port.AC), PortLayout.learn(learned, snap(mapOf(Port.AC to 245))))
    }

    @Test
    fun unknownModelShowsAcAndLearnedPorts() {
        assertEquals(listOf(Port.AC), PortLayout.visible(null, emptySet()))
        assertEquals(listOf(Port.AC, Port.USB_C2), PortLayout.visible(null, setOf(Port.USB_C2)))
    }

    @Test
    fun learnedPortsExtendAKnownModel() {
        val visible = PortLayout.visible(StationModels.DELTA_3_CLASSIC, setOf(Port.DC_12V))
        assertTrue(Port.DC_12V in visible)
    }
}
