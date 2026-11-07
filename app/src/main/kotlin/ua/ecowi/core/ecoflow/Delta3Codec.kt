package ua.ecowi.core.ecoflow

import ua.ecowi.core.proto.ProtoException
import ua.ecowi.core.proto.ProtoReader
import ua.ecowi.core.proto.ProtoValue
import ua.ecowi.core.proto.ProtoWriter
import kotlin.random.Random

/**
 * Codec for the protobuf frames the EcoFlow app exchanges with DELTA 3-family stations over
 * the app MQTT broker (`/app/device/property/{sn}`, `…/thing/property/get_reply`).
 *
 * Frame = HeaderMessage { repeated Header header = 1 }.
 * Header.pdata (field 1) carries the payload; when `enc_type == 1` and `src != 32` the payload
 * is XOR-ed byte-by-byte with the low byte of `seq`.
 * Payload type is chosen by (cmd_func, cmd_id):
 *   254/21 DisplayPropertyUpload — SOC, powers per port, remaining times, states
 *   254/22 RuntimePropertyUpload — voltages, currents, temperatures
 *   3/x    BMS heartbeat
 *
 * Unknown fields are kept as `f<number>` so the diagnostics screen can show them.
 */
object Delta3Codec {

    data class Header(
        val pdata: ByteArray?,
        val src: Int,
        val dest: Int,
        val encType: Int,
        val cmdFunc: Int,
        val cmdId: Int,
        val seq: Int,
        val deviceSn: String?,
    )

    data class Decoded(
        /** Named values ready for QuotaMapper (`cmsBattSoc`, `powGetTypec1`, …). */
        val values: Map<String, Any>,
        /** (cmdFunc, cmdId) pairs seen in the frame, for diagnostics. */
        val kinds: List<Pair<Int, Int>>,
    )

    private val BMS_HEARTBEATS = setOf(3 to 1, 3 to 2, 3 to 30, 3 to 50)

    fun parseHeaders(frame: ByteArray): List<Header> =
        ProtoReader.read(frame)
            .filterIsInstance<ProtoValue.Bytes>()
            .filter { it.number == 1 }
            .map { parseHeader(it.value) }

    private fun parseHeader(bytes: ByteArray): Header {
        var pdata: ByteArray? = null
        val ints = HashMap<Int, Long>()
        var sn: String? = null
        for (f in ProtoReader.read(bytes)) {
            when {
                f is ProtoValue.Bytes && f.number == 1 -> pdata = f.value
                f is ProtoValue.Bytes && f.number == 25 -> sn = f.asTextOrNull()
                f is ProtoValue.Varint -> ints[f.number] = f.value
            }
        }
        fun i(n: Int) = (ints[n] ?: 0L).toInt()
        return Header(pdata, src = i(2), dest = i(3), encType = i(6), cmdFunc = i(8), cmdId = i(9), seq = i(14), deviceSn = sn)
    }

    fun payloadOf(h: Header): ByteArray? {
        val data = h.pdata ?: return null
        if (h.encType != 1 || h.src == 32) return data
        val key = h.seq and 0xff
        return ByteArray(data.size) { i -> (data[i].toInt() xor key).toByte() }
    }

    /** Decodes a whole MQTT frame. Malformed headers are skipped, never thrown. */
    fun decode(frame: ByteArray): Decoded {
        val values = LinkedHashMap<String, Any>()
        val kinds = ArrayList<Pair<Int, Int>>()
        val headers = try {
            parseHeaders(frame)
        } catch (e: ProtoException) {
            return Decoded(emptyMap(), emptyList())
        }
        for (h in headers) {
            kinds += h.cmdFunc to h.cmdId
            val payload = payloadOf(h) ?: continue
            val (table, prefix) = when {
                h.cmdFunc == 254 && h.cmdId == 21 -> Delta3Fields.DISPLAY to ""
                h.cmdFunc == 254 && h.cmdId == 22 -> Delta3Fields.RUNTIME to ""
                (h.cmdFunc to h.cmdId) in BMS_HEARTBEATS -> Delta3Fields.BMS_HEARTBEAT to "bmsHb."
                else -> continue
            }
            try {
                decodeMessage(payload, table, prefix, values)
            } catch (_: ProtoException) {
                // one bad header must not drop the rest of the frame
            }
        }
        return Decoded(values, kinds)
    }

    private fun decodeMessage(payload: ByteArray, table: Map<Int, String>, prefix: String, into: MutableMap<String, Any>) {
        for (f in ProtoReader.read(payload)) {
            val name = prefix + (table[f.number] ?: "f${f.number}")
            val value: Any = when (f) {
                is ProtoValue.Varint -> f.value.toInt().toLong() // int32 negatives come as 64-bit two's complement
                is ProtoValue.Fixed32 -> f.asFloat
                is ProtoValue.Fixed64 -> f.asDouble
                is ProtoValue.Bytes -> f.asTextOrNull() ?: continue // nested / packed: not needed
            }
            if (value is Float && !value.isFinite()) continue
            into[name] = value
        }
    }

    /** Sequence numbers in the app's style (999 9xx xxx). */
    fun newSeq(): Int = 999_900_000 + Random.nextInt(10_000, 100_000)

    /**
     * "Send me everything" request (what the app sends when its device page opens):
     * SendHeaderMsg { msg { src=32 dest=32 seq from } } published to `…/thing/property/get`.
     * The station answers on `get_reply` with a multi-header snapshot.
     */
    fun buildGetAllRequest(seq: Int = newSeq(), from: String = "Android"): ByteArray =
        ProtoWriter().message(1) {
            int(2, 32)
            int(3, 32)
            int(14, seq)
            string(23, from)
        }.toByteArray()
}
