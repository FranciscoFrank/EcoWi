package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.ecowi.core.ecoflow.Delta3Codec
import ua.ecowi.core.proto.ProtoReader
import ua.ecowi.core.proto.ProtoValue
import ua.ecowi.core.proto.ProtoWriter

class Delta3CodecTest {

    /**
     * Fixture produced with Google's protobuf runtime from `ef_delta3.proto`:
     * header 1 = DisplayPropertyUpload, XOR-encrypted with seq 999912345 (enc_type=1, src=2);
     * header 2 = RuntimePropertyUpload, plain (src=32).
     */
    private val frame = hex(
        "0a750a538499999999bc99991adad499993959c49999d958fc99999999719a990c96999935db0989646666666666666666" +
            "982c89999936db79895498718976b76989c34988981c8e9999ec5a138291dddac9cad7a8abaa10021820300140fe0148157099" +
            "e7e5dc03ca010c4433433154455354303030310a110a06a50400806743102040fe0148167005",
    )

    @Test
    fun decodesEncryptedDisplayUpload() {
        val decoded = Delta3Codec.decode(frame)
        val v = decoded.values
        assertEquals(listOf(254 to 21, 254 to 22), decoded.kinds)
        assertEquals(87.5f, v["cmsBattSoc"])
        assertEquals(-245f, v["powGetAcOut"])
        assertEquals(205L, v["cmsDsgRemTime"])
        assertEquals(-3L, v["bmsMinCellTemp"]) // int32 negative
        assertEquals("DCPSN123", v["plugInInfoDcpSn"])
        assertEquals(231.5f, v["plugInInfoAcInVol"]) // from the plain runtime header
    }

    @Test
    fun decodedFrameMapsToSnapshot() {
        val s = QuotaMapper.map(Delta3Codec.decode(frame).values, nowMs = 1L)
        assertEquals(88, s.socPercent) // 87.5 rounds half-up
        assertEquals(245, s.acOutW)
        assertEquals(17, s.dcOutW) // typec1 12 + qcusb1 5
        assertEquals(mapOf(Port.AC to 245, Port.USB_A1 to 5, Port.USB_C1 to 12, Port.USB_C2 to 0), s.ports)
        assertEquals(false, s.gridConnected)
        assertEquals(ChargeState.DISCHARGING, s.chargeState)
        assertEquals(90, s.chargeLimitPercent)
        assertEquals(90, s.fullAtPercent)
        assertEquals(205, s.dischargeRemainMin)
        assertNull(s.chargeRemainMin)
    }

    @Test
    fun buildsGetAllRequestLikeTheReferenceImplementation() {
        assertEquals(
            "0a14102018207091afe8dc03ba0107416e64726f6964",
            Delta3Codec.buildGetAllRequest(seq = 999954321, from = "Android").toHex(),
        )
    }

    @Test
    fun garbageDoesNotThrow() {
        val d = Delta3Codec.decode(byteArrayOf(0x0a, 0x7f, 0x01))
        assertTrue(d.values.isEmpty())
    }

    @Test
    fun writerReaderRoundTrip() {
        val bytes = ProtoWriter().int(3, 300).string(7, "hi").int(9, -1).toByteArray()
        val fields = ProtoReader.read(bytes)
        assertEquals(300L, (fields[0] as ProtoValue.Varint).value)
        assertEquals("hi", (fields[1] as ProtoValue.Bytes).asTextOrNull())
        assertEquals(-1L, (fields[2] as ProtoValue.Varint).value)
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
