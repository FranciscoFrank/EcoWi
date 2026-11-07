package ua.ecowi.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import ua.ecowi.core.QuotaMapper
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * Short-lived connection to the official Open API MQTT broker: connect, listen to
 * `/open/{certificateAccount}/{sn}/quota` until data stops arriving, disconnect.
 * Used when REST `quota/all` is not available for the model (DELTA 3 series).
 */
class OpenMqttSnapshot(
    private val cert: MqttCertificate,
    private val clientId: String,
    private val serialNumber: String,
) {
    suspend fun collect(
        timeoutMs: Long = 20_000,
        quietPeriodMs: Long = 2_500,
    ): Map<String, Any?> = withContext(Dispatchers.IO) {
        val merged = LinkedHashMap<String, Any?>()
        val lock = Any()
        val lastMessageAt = AtomicLong(0L)
        val firstMessage = CompletableDeferred<Unit>()

        val client = MqttClient("ssl://${cert.host}:${cert.port}", clientId, MemoryPersistence())
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                if (!firstMessage.isCompleted) firstMessage.completeExceptionally(cause ?: Exception("lost"))
            }

            override fun messageArrived(topic: String?, message: MqttMessage) {
                val params = parse(message.payload) ?: return
                synchronized(lock) { merged.putAll(QuotaMapper.flatten(params)) }
                lastMessageAt.set(System.currentTimeMillis())
                firstMessage.complete(Unit)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        val options = MqttConnectOptions().apply {
            userName = cert.account
            password = cert.password.toCharArray()
            isCleanSession = true
            isAutomaticReconnect = false
            connectionTimeout = 10
            keepAliveInterval = 30
            socketFactory = SSLSocketFactory.getDefault()
        }

        try {
            client.connect(options)
            client.subscribe("/open/${cert.account}/$serialNumber/quota", 1)
            withTimeoutOrNull(timeoutMs) {
                firstMessage.await()
                // Messages come in bursts (several modules); wait until the burst ends.
                while (System.currentTimeMillis() - lastMessageAt.get() < quietPeriodMs) delay(300)
            }
        } finally {
            runCatching { if (client.isConnected) client.disconnect(2_000) }
            runCatching { client.close() }
        }
        synchronized(lock) { LinkedHashMap(merged) }
    }

    /** Open API pushes JSON; quota values live in `params` (or `param` on some models). */
    private fun parse(payload: ByteArray): Map<String, Any?>? {
        val json = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull() ?: return null
        val body = json.optJSONObject("params") ?: json.optJSONObject("param") ?: json
        return JsonMaps.toMap(body)
    }
}
