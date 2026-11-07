package ua.ecowi.core.proto

/**
 * Minimal protobuf wire-format codec — just what the EcoFlow app protocol needs.
 * No generated classes, no Gradle plugin: messages are read as (field number, wire type, value)
 * and interpreted with the name tables in `Delta3Fields`.
 */
class ProtoException(message: String) : IllegalArgumentException(message)

sealed interface ProtoValue {
    val number: Int

    data class Varint(override val number: Int, val value: Long) : ProtoValue
    data class Fixed32(override val number: Int, val bits: Int) : ProtoValue {
        val asFloat: Float get() = Float.fromBits(bits)
    }
    data class Fixed64(override val number: Int, val bits: Long) : ProtoValue {
        val asDouble: Double get() = Double.fromBits(bits)
    }
    class Bytes(override val number: Int, val value: ByteArray) : ProtoValue {
        /** UTF-8 text if the bytes look like printable text, else null. */
        fun asTextOrNull(): String? {
            if (value.isEmpty()) return ""
            val s = runCatching { value.toString(Charsets.UTF_8) }.getOrNull() ?: return null
            return s.takeIf { str -> str.all { it == '\n' || it == '\t' || (it >= ' ' && it != '�') } }
        }
    }
}

object ProtoReader {

    fun read(bytes: ByteArray): List<ProtoValue> {
        val out = ArrayList<ProtoValue>()
        var pos = 0
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                if (pos >= bytes.size) throw ProtoException("truncated varint")
                val b = bytes[pos++].toInt() and 0xff
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                if (shift >= 64) throw ProtoException("varint too long")
            }
        }
        fun need(n: Int) {
            if (n < 0 || pos + n > bytes.size) throw ProtoException("truncated field")
        }
        while (pos < bytes.size) {
            val tag = varint()
            val number = (tag ushr 3).toInt()
            if (number <= 0) throw ProtoException("bad field number $number")
            when (val wire = (tag and 7).toInt()) {
                0 -> out += ProtoValue.Varint(number, varint())
                1 -> {
                    need(8)
                    var v = 0L
                    for (i in 0 until 8) v = v or ((bytes[pos + i].toLong() and 0xff) shl (8 * i))
                    pos += 8
                    out += ProtoValue.Fixed64(number, v)
                }
                2 -> {
                    val len = varint()
                    if (len > Int.MAX_VALUE) throw ProtoException("length overflow")
                    need(len.toInt())
                    out += ProtoValue.Bytes(number, bytes.copyOfRange(pos, pos + len.toInt()))
                    pos += len.toInt()
                }
                5 -> {
                    need(4)
                    var v = 0
                    for (i in 0 until 4) v = v or ((bytes[pos + i].toInt() and 0xff) shl (8 * i))
                    pos += 4
                    out += ProtoValue.Fixed32(number, v)
                }
                else -> throw ProtoException("unsupported wire type $wire")
            }
        }
        return out
    }
}

class ProtoWriter {
    private val buf = java.io.ByteArrayOutputStream()

    private fun varint(v: Long) {
        var x = v
        while (true) {
            if (x and 0x7fL.inv() == 0L) {
                buf.write(x.toInt())
                return
            }
            buf.write(((x and 0x7f) or 0x80).toInt())
            x = x ushr 7
        }
    }

    private fun tag(number: Int, wire: Int) = varint(((number shl 3) or wire).toLong())

    fun int(number: Int, value: Long): ProtoWriter = apply { tag(number, 0); varint(value) }

    fun int(number: Int, value: Int): ProtoWriter = int(number, value.toLong())

    fun bytes(number: Int, value: ByteArray): ProtoWriter = apply {
        tag(number, 2)
        varint(value.size.toLong())
        buf.write(value)
    }

    fun string(number: Int, value: String): ProtoWriter = bytes(number, value.toByteArray(Charsets.UTF_8))

    fun message(number: Int, build: ProtoWriter.() -> Unit): ProtoWriter =
        bytes(number, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = buf.toByteArray()
}
