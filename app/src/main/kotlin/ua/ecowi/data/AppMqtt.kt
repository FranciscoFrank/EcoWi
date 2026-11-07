package ua.ecowi.data

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttSecurityException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import ua.ecowi.core.QuotaMapper
import ua.ecowi.core.ecoflow.Delta3Codec
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/** Turns an app-MQTT payload into named values: protobuf (DELTA 3 family) or JSON (older models). */
object AppPayload {

    data class Result(val values: Map<String, Any?>, val kinds: List<Pair<Int, Int>>, val format: String)

    fun decode(payload: ByteArray): Result {
        if (payload.isEmpty()) return Result(emptyMap(), emptyList(), "empty")
        // JSON (DELTA 2 and friends): {"params": {...}} or {"data": {"quotaMap": {...}}}
        if (payload[0] == '{'.code.toByte()) {
            val o = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull()
            if (o != null) {
                val body = o.optJSONObject("params")
                    ?: o.optJSONObject("data")?.optJSONObject("quotaMap")
                    ?: o.optJSONObject("param") ?: o
                return Result(QuotaMapper.flatten(JsonMaps.toMap(body)), emptyList(), "json")
            }
        }
        // Some firmwares wrap the protobuf frame in base64.
        if (looksLikeBase64(payload)) {
            val raw = runCatching { java.util.Base64.getDecoder().decode(payload) }.getOrNull()
            if (raw != null) {
                val d = Delta3Codec.decode(raw)
                if (d.values.isNotEmpty()) return Result(d.values, d.kinds, "protobuf+b64")
            }
        }
        val d = Delta3Codec.decode(payload)
        return Result(d.values, d.kinds, "protobuf")
    }

    private fun looksLikeBase64(b: ByteArray): Boolean =
        b.size % 4 == 0 && b.size >= 8 && b.all {
            val c = it.toInt().toChar()
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
        }
}

/**
 * Connection to the EcoFlow **app** MQTT broker for one station.
 *  - telemetry: `/app/device/property/{sn}` (pushed by the station, deltas)
 *  - snapshot:  publish to `/app/{userId}/{sn}/thing/property/get`, answer on `…/get_reply`
 */
class AppMqttConnection(
    private val session: AppSession,
    private val clientId: String,
    private val serialNumber: String,
    private val onValues: (AppPayload.Result, fromSnapshot: Boolean) -> Unit,
    private val onConnected: (Boolean) -> Unit = {},
) {
    private val propertyTopic = "/app/device/property/$serialNumber"
    private val getTopic = "/app/${session.userId}/$serialNumber/thing/property/get"
    private val getReplyTopic = "/app/${session.userId}/$serialNumber/thing/property/get_reply"

    private var client: MqttClient? = null

    val isConnected: Boolean get() = client?.isConnected == true

    /** Blocking. Throws [EcoFlowAuthException] when the broker rejects the certificate. */
    fun connect(autoReconnect: Boolean) {
        val c = MqttClient("ssl://${session.mqtt.host}:${session.mqtt.port}", clientId, MemoryPersistence())
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                // Clean session: subscriptions are lost on reconnect, restore them.
                runCatching { subscribe(c) }.onFailure { Log.w(TAG, "resubscribe failed", it) }
                if (reconnect) runCatching { requestAll() }
                onConnected(true)
            }

            override fun connectionLost(cause: Throwable?) = onConnected(false)

            override fun messageArrived(topic: String?, message: MqttMessage) {
                val decoded = runCatching { AppPayload.decode(message.payload) }.getOrNull() ?: return
                if (decoded.values.isNotEmpty()) onValues(decoded, topic == getReplyTopic)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        val options = MqttConnectOptions().apply {
            userName = session.mqtt.account
            password = session.mqtt.password.toCharArray()
            isCleanSession = true
            isAutomaticReconnect = autoReconnect
            connectionTimeout = 15
            keepAliveInterval = 60
            maxReconnectDelay = 120_000
            socketFactory = SSLSocketFactory.getDefault()
        }
        try {
            c.connect(options)
        } catch (e: MqttSecurityException) {
            runCatching { c.close() }
            throw EcoFlowAuthException("MQTT: ${e.message ?: "not authorized"}")
        } catch (e: MqttException) {
            runCatching { c.close() }
            throw e
        }
        client = c
        // connectComplete() isn't guaranteed to run before connect() returns on every Paho build.
        if (runCatching { subscribe(c) }.isFailure) Log.w(TAG, "initial subscribe failed")
    }

    private fun subscribe(c: MqttClient) {
        c.subscribe(arrayOf(propertyTopic, getReplyTopic), intArrayOf(1, 1))
    }

    /** Ask the station for a full state dump (what the app does when you open the device). */
    fun requestAll() {
        val c = client ?: return
        if (!c.isConnected) return
        c.publish(getTopic, MqttMessage(Delta3Codec.buildGetAllRequest()).apply { qos = 1 })
    }

    fun disconnect() {
        val c = client ?: return
        client = null
        runCatching { if (c.isConnected) c.disconnect(2_000) }
        runCatching { c.close() }
    }

    companion object {
        private const val TAG = "AppMqtt"

        /**
         * One-off: connect, request a snapshot, collect until the reply (plus a short quiet
         * period for trailing pushes) or [timeoutMs], disconnect.
         */
        suspend fun snapshot(
            session: AppSession,
            clientId: String,
            serialNumber: String,
            timeoutMs: Long = 15_000,
        ): AppPayload.Result = withContext(Dispatchers.IO) {
            val merged = LinkedHashMap<String, Any?>()
            val kinds = ArrayList<Pair<Int, Int>>()
            val lock = Any()
            val lastAt = AtomicLong(0L)
            val gotReply = CompletableDeferred<Unit>()
            var format = "none"

            val conn = AppMqttConnection(session, clientId, serialNumber, onValues = { r, fromSnapshot ->
                synchronized(lock) {
                    merged.putAll(r.values)
                    kinds += r.kinds
                    format = r.format
                }
                lastAt.set(System.currentTimeMillis())
                if (fromSnapshot) gotReply.complete(Unit)
            })
            try {
                conn.connect(autoReconnect = false)
                conn.requestAll()
                withTimeoutOrNull(timeoutMs) {
                    gotReply.await()
                    while (System.currentTimeMillis() - lastAt.get() < 1_500) delay(250)
                }
            } finally {
                conn.disconnect()
            }
            synchronized(lock) { AppPayload.Result(LinkedHashMap(merged), kinds.distinct(), format) }
        }
    }
}
