package ua.ecowi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.ecowi.core.EcoFlowSigner
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/** API answered, but with a non-success code (e.g. device model not exposed via REST). */
class EcoFlowApiException(
    message: String,
    val apiCode: String? = null,
    val httpStatus: Int? = null,
) : IOException(message) {
    /** True when retrying soon will not help (auth / unsupported device), unlike network errors. */
    val isRejection: Boolean get() = apiCode != null || (httpStatus != null && httpStatus in 400..499)
}

data class DeviceInfo(val sn: String, val name: String, val productName: String?, val online: Boolean)

data class MqttCertificate(val host: String, val port: Int, val account: String, val password: String)

/**
 * Official EcoFlow IoT Open API (developer-eu.ecoflow.com / developer.ecoflow.com).
 * Signed GET requests: see [EcoFlowSigner].
 */
class EcoFlowOpenApi(private val credentials: Credentials) {

    suspend fun quotaAll(): Map<String, Any?> {
        val data = get("/iot-open/sign/device/quota/all", mapOf("sn" to credentials.serialNumber))
        return (data as? JSONObject)?.let(JsonMaps::toMap)
            ?: throw EcoFlowApiException("quota/all: empty data")
    }

    suspend fun deviceList(): List<DeviceInfo> {
        val data = get("/iot-open/sign/device/list", emptyMap()) as? JSONArray ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val sn = o.optString("sn").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DeviceInfo(
                sn = sn,
                name = o.optString("deviceName").ifBlank { sn },
                productName = o.optString("productName").takeIf { it.isNotBlank() },
                online = o.opt("online")?.toString() == "1",
            )
        }
    }

    suspend fun mqttCertificate(): MqttCertificate {
        val o = get("/iot-open/sign/certification", emptyMap()) as? JSONObject
            ?: throw EcoFlowApiException("certification: empty data")
        return MqttCertificate(
            host = o.optString("url").ifBlank { throw EcoFlowApiException("certification: no url") },
            port = o.optString("port").toIntOrNull() ?: 8883,
            account = o.optString("certificateAccount"),
            password = o.optString("certificatePassword"),
        )
    }

    /** Returns the `data` field of a successful response (JSONObject, JSONArray or null). */
    private suspend fun get(path: String, params: Map<String, Any?>): Any? = withContext(Dispatchers.IO) {
        val nonce = Random.nextInt(100_000, 1_000_000).toString()
        val timestamp = System.currentTimeMillis().toString()
        val sign = EcoFlowSigner.sign(params, credentials.accessKey, credentials.secretKey, nonce, timestamp)

        val query = EcoFlowSigner.flatten(params).sortedBy { it.first }
            .joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val url = URL("https://${credentials.region.host}$path" + if (query.isEmpty()) "" else "?$query")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("accessKey", credentials.accessKey)
            setRequestProperty("nonce", nonce)
            setRequestProperty("timestamp", timestamp)
            setRequestProperty("sign", sign)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = conn.responseCode
            val body = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: throw EcoFlowApiException("HTTP $status: ${body.take(120)}", httpStatus = status)
            val code = json.opt("code")?.toString()
            if (status !in 200..299 || code != "0") {
                val msg = json.optString("message").ifBlank { "HTTP $status" }
                throw EcoFlowApiException("$msg (code=$code)", apiCode = code, httpStatus = status)
            }
            json.opt("data")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

object JsonMaps {
    fun toMap(o: JSONObject): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = unwrap(o.opt(k))
        }
        return out
    }

    private fun unwrap(v: Any?): Any? = when (v) {
        is JSONObject -> toMap(v)
        is JSONArray -> (0 until v.length()).map { unwrap(v.opt(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}
