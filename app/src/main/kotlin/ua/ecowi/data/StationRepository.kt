package ua.ecowi.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import ua.ecowi.core.ChargeState
import ua.ecowi.core.DetectorState
import ua.ecowi.core.EventDetector
import ua.ecowi.core.LinkCheck
import ua.ecowi.core.LinkDetector
import ua.ecowi.core.LinkEvent
import ua.ecowi.core.LinkState
import ua.ecowi.core.Port
import ua.ecowi.core.PortLayout
import ua.ecowi.core.PowerSnapshot
import ua.ecowi.core.QuotaMapper
import ua.ecowi.core.StationEvent
import ua.ecowi.core.StationModels

/** Which channel delivered the data. */
enum class DataSource { APP_MQTT, REST, OPEN_MQTT }

sealed interface FetchResult {
    data class Ok(
        val snapshot: PowerSnapshot,
        val source: DataSource,
        val rawKeys: Map<String, Any?>,
        /** Diagnostics: why a channel was skipped/failed, payload format, message kinds. */
        val note: String? = null,
    ) : FetchResult

    data class Failed(
        val message: String,
        val authProblem: Boolean = false,
        val kind: FailureKind = if (authProblem) FailureKind.AUTH else FailureKind.OTHER,
    ) : FetchResult
}

/** Why a fetch failed: only the first two say something about the station itself. */
enum class FailureKind {
    /** EcoFlow's cloud answered but the station sent nothing. */
    STATION_SILENT,

    /** EcoFlow's cloud reports the station offline (Developer API device list). */
    STATION_OFFLINE,

    /** The phone has no working internet connection. */
    NETWORK,
    AUTH,
    NO_CREDENTIALS,
    OTHER,
}

/** Last known state, persisted so widgets render instantly (and offline). */
data class CachedState(
    val snapshot: PowerSnapshot?,
    val source: DataSource?,
    val lastError: String?,
    val lastAttemptMs: Long,
)

class StationRepository(context: Context) {
    private val app = context.applicationContext
    private val settings = AppSettings(app)
    private val cache = SnapshotCache(app)

    /** Background refresh: fetch with saved settings, persist, raise notifications. */
    suspend fun refreshAndStore(): FetchResult {
        val result = when (settings.source) {
            DataSourceKind.APP_ACCOUNT -> settings.account?.let { fetchApp(it) }
            DataSourceKind.DEVELOPER_API -> settings.credentials?.let { fetchDeveloper(it, useRestBackoff = true) }
        } ?: FetchResult.Failed(NO_CREDENTIALS, kind = FailureKind.NO_CREDENTIALS)

        val now = System.currentTimeMillis()
        val check = when (result) {
            is FetchResult.Ok -> LinkCheck.Reached(now, store(result.snapshot, result.source, result.rawKeys).socPercent)
            is FetchResult.Failed -> {
                cache.saveError(result.message)
                when (result.kind) {
                    FailureKind.STATION_SILENT -> LinkCheck.Silent(now)
                    FailureKind.STATION_OFFLINE -> LinkCheck.Silent(now, cloudSaysOffline = true)
                    else -> LinkCheck.Inconclusive
                }
            }
        }
        LinkDispatcher.process(app, check)
        return result
    }

    /** Live mode watchdog: the connection is up but the station hasn't answered a "get all" request. */
    fun reportSilent(atMs: Long = System.currentTimeMillis()): LinkState =
        LinkDispatcher.process(app, LinkCheck.Silent(atMs))

    /**
     * Merges values that arrived on a live connection, persists them and runs event detection.
     * Returns the merged snapshot.
     */
    fun applyLive(values: Map<String, Any?>): PowerSnapshot {
        val now = System.currentTimeMillis()
        val merged = store(QuotaMapper.map(values, now), DataSource.APP_MQTT, values)
        LinkDispatcher.process(app, LinkCheck.Reached(now, merged.socPercent))
        return merged
    }

    private fun store(fresh: PowerSnapshot, source: DataSource, raw: Map<String, Any?>): PowerSnapshot {
        val merged = fresh.fillFrom(cache.load().snapshot)
        cache.saveSnapshot(merged, source)
        cache.mergeRaw(raw)
        val known = cache.learnedPorts()
        val learned = PortLayout.learn(known, merged)
        if (learned != known) cache.saveLearnedPorts(learned)
        EventDispatcher.process(app, merged)
        return merged
    }

    // ------------------------------------------------------------------ app account

    /** Login (cached) → app MQTT → "get all" snapshot. Re-logs in once if the session expired. */
    suspend fun fetchApp(account: AccountCredentials, forceLogin: Boolean = false, persistSession: Boolean = true): FetchResult {
        return try {
            val session = sessionFor(account, forceLogin, persistSession)
            val result = AppMqttConnection.snapshot(
                session, settings.appMqttClientId(session.userId, live = false), account.serialNumber,
            )
            val snapshot = QuotaMapper.map(result.values, System.currentTimeMillis())
            if (snapshot.hasAnyData) {
                FetchResult.Ok(
                    snapshot, DataSource.APP_MQTT, result.values,
                    note = "format=${result.format}; kinds=${result.kinds.joinToString { "${it.first}/${it.second}" }}",
                )
            } else {
                if (result.values.isEmpty()) {
                    FetchResult.Failed("no data from the station in 15 s (is it online?)", kind = FailureKind.STATION_SILENT)
                } else {
                    FetchResult.Failed("unknown payload (${result.values.size} keys, ${result.format})")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: EcoFlowAuthException) {
            if (!forceLogin && settings.appSession != null) fetchApp(account, forceLogin = true, persistSession = persistSession)
            else FetchResult.Failed("login: ${e.message}", authProblem = true)
        } catch (e: Exception) {
            FetchResult.Failed(e.message ?: e.javaClass.simpleName, kind = classify(e))
        }
    }

    private fun classify(e: Throwable): FailureKind =
        if (!Net.isOnline(app) || Net.isNetworkError(e)) FailureKind.NETWORK else FailureKind.OTHER

    suspend fun sessionFor(account: AccountCredentials, forceLogin: Boolean = false, persist: Boolean = true): AppSession {
        val cached = settings.appSession
        if (!forceLogin && cached != null && System.currentTimeMillis() - cached.obtainedAtMs < SESSION_MAX_AGE_MS) {
            return cached
        }
        val fresh = EcoFlowAppApi(account.host).login(account.email, account.password)
        if (persist) settings.appSession = fresh
        return fresh
    }

    // ------------------------------------------------------------------ developer API

    /**
     * REST `quota/all` first (one request, cheap). If the API rejects it — DELTA 3 series often
     * does — fall back to a short Open API MQTT session using the same developer keys.
     */
    suspend fun fetchDeveloper(creds: Credentials, useRestBackoff: Boolean = false): FetchResult {
        val api = EcoFlowOpenApi(creds)
        val now = System.currentTimeMillis()
        var restNote: String

        if (!useRestBackoff || now >= settings.restBlockedUntilMs) {
            try {
                val raw = api.quotaAll()
                val snapshot = QuotaMapper.map(raw, now)
                if (snapshot.socPercent != null) {
                    detectModel(api, creds.serialNumber)
                    return FetchResult.Ok(snapshot, DataSource.REST, raw)
                }
                restNote = "REST: no battery level among ${raw.size} keys"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                restNote = "REST: ${e.message}"
                if (e is EcoFlowApiException && e.isRejection && useRestBackoff) {
                    settings.restBlockedUntilMs = now + REST_BACKOFF_MS
                }
            }
        } else {
            restNote = "REST: skipped (rejected recently)"
        }

        return try {
            val cert = api.mqttCertificate()
            val raw = OpenMqttSnapshot(cert, settings.mqttClientId, creds.serialNumber).collect()
            val snapshot = QuotaMapper.map(raw, System.currentTimeMillis())
            if (snapshot.hasAnyData) {
                detectModel(api, creds.serialNumber)
                FetchResult.Ok(snapshot, DataSource.OPEN_MQTT, raw, restNote)
            } else if (raw.isEmpty()) {
                // The cloud knows whether the station is connected: ask before blaming it.
                val device = runCatching { api.deviceList().firstOrNull { it.sn == creds.serialNumber } }.getOrNull()
                device?.let { rememberModel(it.productName) }
                FetchResult.Failed(
                    "$restNote; MQTT: no messages in 20 s" + if (device?.online == false) " (cloud: station offline)" else "",
                    kind = if (device?.online == false) FailureKind.STATION_OFFLINE else FailureKind.STATION_SILENT,
                )
            } else {
                FetchResult.Failed("$restNote; MQTT: no known keys among ${raw.size}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.Failed("$restNote; MQTT: ${e.message ?: e.javaClass.simpleName}", kind = classify(e))
        }
    }

    /** Asks the cloud for the product name at most once a day until a known model is found. */
    private suspend fun detectModel(api: EcoFlowOpenApi, serialNumber: String) {
        val now = System.currentTimeMillis()
        if (settings.detectedModelId != null || now - settings.modelProbeAtMs < MODEL_PROBE_EVERY_MS) return
        settings.modelProbeAtMs = now
        runCatching { api.deviceList().firstOrNull { it.sn == serialNumber } }.getOrNull()
            ?.let { rememberModel(it.productName) }
    }

    fun rememberModel(productName: String?) {
        StationModels.match(productName)?.let { settings.detectedModelId = it.id }
    }

    companion object {
        const val NO_CREDENTIALS = "no_credentials"
        private const val REST_BACKOFF_MS = 12 * 60 * 60_000L
        private const val SESSION_MAX_AGE_MS = 7 * 24 * 60 * 60_000L
        private const val MODEL_PROBE_EVERY_MS = 24 * 60 * 60_000L
    }
}

/** Runs [EventDetector] on every stored snapshot and posts notifications. */
object EventDispatcher {
    private val lock = Any()

    fun process(context: Context, snapshot: PowerSnapshot) {
        val events: List<StationEvent>
        synchronized(lock) {
            val store = EventStateStore(context)
            val (next, ev) = EventDetector.process(store.load(), snapshot, AppSettings(context).eventConfig)
            store.save(next)
            events = ev
        }
        if (events.isNotEmpty()) ua.ecowi.notify.Notifier.post(context, events, snapshot)
    }
}

class SnapshotCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ecowi_cache", Context.MODE_PRIVATE)

    fun load(): CachedState = CachedState(
        snapshot = prefs.getString("snapshot", null)?.let(::snapshotFromJson),
        source = prefs.getString("source", null)?.let { runCatching { DataSource.valueOf(it) }.getOrNull() },
        lastError = prefs.getString("error", null),
        lastAttemptMs = prefs.getLong("attempt_at", 0L),
    )

    fun saveSnapshot(s: PowerSnapshot, source: DataSource) {
        prefs.edit()
            .putString("snapshot", snapshotToJson(s))
            .putString("source", source.name)
            .remove("error")
            .putLong("attempt_at", System.currentTimeMillis())
            .apply()
    }

    fun saveError(message: String) {
        prefs.edit().putString("error", message).putLong("attempt_at", System.currentTimeMillis()).apply()
    }

    /** Raw key/values for the diagnostics screen (merged, capped). */
    fun mergeRaw(raw: Map<String, Any?>) {
        if (raw.isEmpty()) return
        val current = loadRaw().toMutableMap()
        raw.forEach { (k, v) -> if (v != null) current[k] = v.toString() }
        val capped = current.entries.sortedBy { it.key }.take(800)
        val o = JSONObject()
        capped.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("raw", o.toString()).putLong("raw_at", System.currentTimeMillis()).apply()
    }

    fun loadRaw(): Map<String, String> {
        val o = prefs.getString("raw", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { out[it] = o.optString(it) }
        return out
    }

    /** Ports that have drawn power at least once (see [PortLayout.learn]). */
    fun learnedPorts(): Set<Port> =
        prefs.getStringSet("learned_ports", emptySet()).orEmpty()
            .mapNotNull { runCatching { Port.valueOf(it) }.getOrNull() }.toSet()

    fun saveLearnedPorts(ports: Set<Port>) {
        prefs.edit().putStringSet("learned_ports", ports.map { it.name }.toSet()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    companion object {
        fun snapshotToJson(s: PowerSnapshot): String = JSONObject().apply {
            putOpt("soc", s.socPercent)
            putOpt("ac", s.acOutW)
            putOpt("dc", s.dcOutW)
            putOpt("in", s.totalInW)
            putOpt("out", s.totalOutW)
            putOpt("dsg", s.dischargeRemainMin)
            putOpt("chg", s.chargeRemainMin)
            put("at", s.fetchedAtMs)
            put("ports", JSONObject().apply { s.ports.forEach { (p, w) -> put(p.name, w) } })
            putOpt("grid", s.gridConnected)
            putOpt("acIn", s.acInW)
            putOpt("pv", s.solarInW)
            putOpt("state", s.chargeState?.name)
            putOpt("limit", s.chargeLimitPercent)
        }.toString()

        fun snapshotFromJson(json: String): PowerSnapshot? = runCatching {
            val o = JSONObject(json)
            fun int(k: String): Int? = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
            val ports = o.optJSONObject("ports")?.let { p ->
                p.keys().asSequence().mapNotNull { k ->
                    runCatching { Port.valueOf(k) }.getOrNull()?.let { it to p.getInt(k) }
                }.toMap()
            } ?: emptyMap()
            PowerSnapshot(
                socPercent = int("soc"),
                acOutW = int("ac"),
                dcOutW = int("dc"),
                totalInW = int("in"),
                totalOutW = int("out"),
                dischargeRemainMin = int("dsg"),
                chargeRemainMin = int("chg"),
                fetchedAtMs = o.getLong("at"),
                ports = ports,
                gridConnected = if (o.has("grid") && !o.isNull("grid")) o.getBoolean("grid") else null,
                acInW = int("acIn"),
                solarInW = int("pv"),
                chargeState = o.optString("state").takeIf { it.isNotBlank() }
                    ?.let { runCatching { ChargeState.valueOf(it) }.getOrNull() },
                chargeLimitPercent = int("limit"),
            )
        }.getOrNull()
    }
}

/** Persists [DetectorState] between process restarts. */
class EventStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ecowi_events", Context.MODE_PRIVATE)

    fun load(): DetectorState {
        val o = prefs.getString("state", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return DetectorState()
        fun portMap(key: String): Map<Port, Long> = o.optJSONObject(key)?.let { m ->
            m.keys().asSequence().mapNotNull { k -> runCatching { Port.valueOf(k) }.getOrNull()?.let { it to m.getLong(k) } }.toMap()
        } ?: emptyMap()
        return DetectorState(
            seeded = o.optBoolean("seeded"),
            firedLow = o.optJSONArray("firedLow")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            fullFired = o.optBoolean("fullFired"),
            charging = if (o.has("charging")) o.getBoolean("charging") else null,
            lastChargingStartMs = o.optLong("lastStart"),
            grid = if (o.has("grid")) o.getBoolean("grid") else null,
            activePorts = portMap("active"),
            idlePorts = portMap("idle"),
        )
    }

    fun save(s: DetectorState) {
        val o = JSONObject()
            .put("seeded", s.seeded)
            .put("firedLow", JSONArray(s.firedLow.toList()))
            .put("fullFired", s.fullFired)
            .put("lastStart", s.lastChargingStartMs)
            .put("active", JSONObject().apply { s.activePorts.forEach { (p, t) -> put(p.name, t) } })
            .put("idle", JSONObject().apply { s.idlePorts.forEach { (p, t) -> put(p.name, t) } })
        s.charging?.let { o.put("charging", it) }
        s.grid?.let { o.put("grid", it) }
        prefs.edit().putString("state", o.toString()).apply()
    }

    fun reset() = prefs.edit().clear().apply()
}

/**
 * Runs [LinkDetector] on every attempt to reach the station and posts the "isn't responding" /
 * "back online" notifications.
 */
object LinkDispatcher {
    private val lock = Any()

    fun process(context: Context, check: LinkCheck): LinkState {
        val event: LinkEvent?
        val next: LinkState
        synchronized(lock) {
            val store = LinkStore(context)
            val prev = store.load()
            val (n, e) = LinkDetector.process(prev, check)
            next = n
            event = e
            // Live mode reports data every few seconds; only persist when something meaningful changed.
            val seenMoved = (n.lastSeenMs ?: 0L) - (prev.lastSeenMs ?: 0L) >= 60_000L || (prev.lastSeenMs == null && n.lastSeenMs != null)
            if (e != null || n.down != prev.down || n.silentChecks != prev.silentChecks || n.lastSoc != prev.lastSoc || seenMoved) {
                store.save(n)
            }
        }
        if (event != null) ua.ecowi.notify.Notifier.postLink(context, event)
        return next
    }
}

/** Persists [LinkState] (prefs "ecowi_link"). */
class LinkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ecowi_link", Context.MODE_PRIVATE)

    fun load(): LinkState = LinkState(
        down = prefs.getBoolean("down", false),
        silentChecks = prefs.getInt("silent", 0),
        lastSeenMs = prefs.getLong("seen", -1L).takeIf { it >= 0 },
        lastSoc = prefs.getInt("soc", -1).takeIf { it >= 0 },
    )

    fun save(s: LinkState) {
        prefs.edit()
            .putBoolean("down", s.down)
            .putInt("silent", s.silentChecks)
            .putLong("seen", s.lastSeenMs ?: -1L)
            .putInt("soc", s.lastSoc ?: -1)
            .apply()
    }

    fun reset() = prefs.edit().clear().apply()
}
