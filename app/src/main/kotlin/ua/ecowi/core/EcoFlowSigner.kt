package ua.ecowi.core

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Request signing for the official EcoFlow IoT Open API (developer platform).
 *
 * Algorithm (same as the documented examples and community clients):
 *  1. Flatten request params: nested objects -> `a.b`, arrays -> `a[0]`.
 *  2. Sort by key (ASCII order) and join as `k=v&k2=v2`.
 *  3. Append `accessKey=..&nonce=..&timestamp=..` (with `&` if step 2 is not empty).
 *  4. sign = hex(HMAC-SHA256(secretKey, string)).
 *
 * Headers sent with the request: accessKey, nonce, timestamp, sign.
 * Pure Kotlin/JVM: no Android imports, so it is unit-testable on the JVM.
 */
object EcoFlowSigner {

    fun flatten(params: Map<String, Any?>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        fun walk(prefix: String, value: Any?) {
            when (value) {
                null -> Unit
                is Map<*, *> -> value.forEach { (k, v) ->
                    val key = if (prefix.isEmpty()) k.toString() else "$prefix.$k"
                    walk(key, v)
                }
                is List<*> -> value.forEachIndexed { i, v -> walk("$prefix[$i]", v) }
                is Array<*> -> value.forEachIndexed { i, v -> walk("$prefix[$i]", v) }
                else -> out += prefix to value.toString()
            }
        }
        params.forEach { (k, v) -> walk(k, v) }
        return out
    }

    /** Canonical, sorted `k=v&...` string used both for the signature and the GET query. */
    fun canonicalQuery(params: Map<String, Any?>): String =
        flatten(params).sortedBy { it.first }.joinToString("&") { (k, v) -> "$k=$v" }

    fun stringToSign(
        params: Map<String, Any?>,
        accessKey: String,
        nonce: String,
        timestamp: String,
    ): String {
        val tail = "accessKey=$accessKey&nonce=$nonce&timestamp=$timestamp"
        val query = canonicalQuery(params)
        return if (query.isEmpty()) tail else "$query&$tail"
    }

    fun sign(
        params: Map<String, Any?>,
        accessKey: String,
        secretKey: String,
        nonce: String,
        timestamp: String,
    ): String = hmacSha256Hex(stringToSign(params, accessKey, nonce, timestamp), secretKey)

    fun hmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
