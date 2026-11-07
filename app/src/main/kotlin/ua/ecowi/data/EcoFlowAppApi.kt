package ua.ecowi.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Wrong e-mail / password (no point retrying until the user fixes it). */
class EcoFlowAuthException(message: String) : IOException(message)

/** Login result: bearer token + MQTT certificate for the app broker. */
data class AppSession(
    val token: String,
    val userId: String,
    val userName: String?,
    val mqtt: MqttCertificate,
    val obtainedAtMs: Long,
) {
    fun toJson(): String = JSONObject()
        .put("token", token).put("userId", userId).put("userName", userName)
        .put("host", mqtt.host).put("port", mqtt.port)
        .put("account", mqtt.account).put("password", mqtt.password)
        .put("at", obtainedAtMs)
        .toString()

    companion object {
        fun fromJson(s: String): AppSession? = runCatching {
            val o = JSONObject(s)
            AppSession(
                token = o.getString("token"),
                userId = o.getString("userId"),
                userName = o.optString("userName").takeIf { it.isNotBlank() && it != "null" },
                mqtt = MqttCertificate(o.getString("host"), o.getInt("port"), o.getString("account"), o.getString("password")),
                obtainedAtMs = o.getLong("at"),
            )
        }.getOrNull()
    }
}

/**
 * The login flow of the EcoFlow mobile app (unofficial, documented by the open-source
 * Home Assistant integration tolwi/hassio-ecoflow-cloud):
 *   POST /auth/login {email, password: base64, scene: IOT_APP, userType: ECOFLOW} → token, userId
 *   GET  /iot-auth/app/certification (Bearer) → MQTT host/port/account/password
 */
class EcoFlowAppApi(private val host: String = AccountCredentials.DEFAULT_APP_HOST) {

    suspend fun login(email: String, password: String): AppSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", Base64.encodeToString(password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("scene", "IOT_APP")
            .put("userType", "ECOFLOW")
        val json = try {
            request("POST", "/auth/login", body = body.toString())
        } catch (e: EcoFlowApiException) {
            // The API answered with an error code: credentials rejected.
            if (e.apiCode != null) throw EcoFlowAuthException(e.message ?: "login failed") else throw e
        }
        val data = json.optJSONObject("data") ?: throw EcoFlowApiException("login: no data")
        val token = data.optString("token").ifBlank { throw EcoFlowApiException("login: no token") }
        val user = data.optJSONObject("user") ?: throw EcoFlowApiException("login: no user")
        val userId = user.opt("userId")?.toString()?.takeIf { it.isNotBlank() }
            ?: throw EcoFlowApiException("login: no userId")
        val cert = certification(token, userId)
        AppSession(token, userId, user.optString("name").takeIf { it.isNotBlank() }, cert, System.currentTimeMillis())
    }

    suspend fun certification(token: String, userId: String): MqttCertificate = withContext(Dispatchers.IO) {
        val json = request("GET", "/iot-auth/app/certification?userId=" + URLEncoder.encode(userId, "UTF-8"), token = token)
        val d = json.optJSONObject("data") ?: throw EcoFlowApiException("certification: no data")
        MqttCertificate(
            host = d.optString("url").ifBlank { throw EcoFlowApiException("certification: no url") },
            port = d.optString("port").toIntOrNull() ?: 8883,
            account = d.optString("certificateAccount"),
            password = d.optString("certificatePassword"),
        )
    }

    private fun request(method: String, path: String, body: String? = null, token: String? = null): JSONObject {
        val conn = (URL("https://$host$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("lang", "en_US")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val status = conn.responseCode
            if (status == 401 || status == 403) throw EcoFlowAuthException("HTTP $status")
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: throw EcoFlowApiException("HTTP $status: ${text.take(120)}", httpStatus = status)
            val code = json.opt("code")?.toString()
            val message = json.optString("message")
            val ok = status in 200..299 && if (code != null) code == "0" else message.equals("success", ignoreCase = true)
            if (!ok) {
                throw EcoFlowApiException("${message.ifBlank { "HTTP $status" }} (code=$code)", apiCode = code, httpStatus = status)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}
