package ua.ecowi.live

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.ecowi.appwidget.Widgets
import ua.ecowi.core.PowerSnapshot
import ua.ecowi.data.AppMqttConnection
import ua.ecowi.data.AppSettings
import ua.ecowi.data.DataSourceKind
import ua.ecowi.data.EcoFlowAuthException
import ua.ecowi.data.SnapshotCache
import ua.ecowi.data.LinkStore
import ua.ecowi.data.StationRepository
import ua.ecowi.notify.Notifier
import ua.ecowi.work.RefreshScheduler
import java.util.concurrent.ConcurrentHashMap

/**
 * "Live mode": a foreground service holding one connection to the EcoFlow app MQTT broker.
 * Station pushes arrive within seconds → widgets, the live notification (Now Bar on One UI 8)
 * and alerts update immediately. Without it the app falls back to WorkManager refreshes.
 *
 * Foreground type `specialUse` (Android 14+): none of the predefined types fits
 * "keep a cloud connection to my own power station"; fine for a sideloaded personal app.
 */
class LiveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connection: AppMqttConnection? = null
    private var loop: Job? = null

    /** Values received since the last flush (pushes arrive in bursts of many keys). */
    private val pending = ConcurrentHashMap<String, Any>()
    @Volatile private var connected = false
    @Volatile private var lastSnapshot: PowerSnapshot? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lastSnapshot = SnapshotCache(this).load().snapshot
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        connection?.disconnect()
        connection = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val n = Notifier.live(this, lastSnapshot, connected = false)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID_LIVE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID_LIVE, n)
        }
    }

    private suspend fun runLoop() {
        val settings = AppSettings(this)
        val repo = StationRepository(this)
        var backoffMs = 5_000L
        var forceLogin = false

        while (scope.isActive) {
            val account = settings.account
            if (settings.source != DataSourceKind.APP_ACCOUNT || account == null) {
                Log.i(TAG, "live mode needs the app-account source; stopping")
                stopSelf()
                return
            }
            try {
                val session = repo.sessionFor(account, forceLogin)
                forceLogin = false
                val conn = AppMqttConnection(
                    session,
                    settings.appMqttClientId(session.userId, live = true),
                    account.serialNumber,
                    onValues = { r, _ -> r.values.forEach { (k, v) -> if (v != null) pending[k] = v } },
                    onConnected = { ok ->
                        connected = ok
                        updateNotification()
                    },
                )
                conn.connect(autoReconnect = true)
                connection = conn
                connected = true
                conn.requestAll()
                backoffMs = 5_000L

                var sinceFullRequest = 0L
                var lastConnectedAt = System.currentTimeMillis()
                lastDataAt = System.currentTimeMillis()
                var lastProbeAt = 0L
                while (scope.isActive) {
                    delay(FLUSH_MS)
                    flush(repo)
                    sinceFullRequest += FLUSH_MS
                    if (sinceFullRequest >= FULL_REQUEST_EVERY_MS) {
                        sinceFullRequest = 0
                        runCatching { conn.requestAll() }
                    }
                    // Watchdog: the cloud connection is up but the station has gone quiet. An idle station
                    // still answers "get all", so ask; if it doesn't, LinkDetector decides when to alert.
                    val now = System.currentTimeMillis()
                    if (conn.isConnected && now - lastDataAt > SILENCE_PROBE_MS && now - lastProbeAt >= PROBE_EVERY_MS) {
                        lastProbeAt = now
                        runCatching { conn.requestAll() }
                        if (now - lastDataAt > SILENCE_PROBE_MS + FLUSH_MS) {
                            repo.reportSilent(now)
                            updateNotification()
                        }
                    }
                    if (conn.isConnected) {
                        lastConnectedAt = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - lastConnectedAt > RELOGIN_AFTER_MS) {
                        // Paho keeps retrying the same certificate; it may have expired.
                        throw EcoFlowAuthException("offline for too long")
                    }
                }
            } catch (e: EcoFlowAuthException) {
                Log.w(TAG, "auth failed, will re-login", e)
                forceLogin = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "live connection failed", e)
            }
            connection?.disconnect()
            connection = null
            connected = false
            updateNotification()
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(5 * 60_000L)
        }
    }

    private var lastWidgetRenderAt = 0L

    @Volatile
    private var lastDataAt = 0L

    private fun flush(repo: StationRepository) {
        if (pending.isEmpty()) return
        val batch = HashMap<String, Any?>(pending)
        batch.keys.forEach { pending.remove(it) }
        lastDataAt = System.currentTimeMillis()
        val snap = repo.applyLive(batch) // also runs alert detection
        lastSnapshot = snap
        val now = System.currentTimeMillis()
        if (now - lastWidgetRenderAt >= WIDGET_MIN_INTERVAL_MS) {
            lastWidgetRenderAt = now
            Widgets.renderAll(this)
        }
        updateNotification()
    }

    private fun updateNotification() {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(Notifier.ID_LIVE, Notifier.live(this, lastSnapshot, connected, LinkStore(this).load()))
    }

    companion object {
        /** Ask the station again after this long without data, then at most every [PROBE_EVERY_MS]. */
        private const val SILENCE_PROBE_MS = 90_000L
        private const val PROBE_EVERY_MS = 60_000L
        private const val TAG = "LiveService"
        private const val FLUSH_MS = 3_000L
        private const val FULL_REQUEST_EVERY_MS = 5 * 60_000L
        private const val WIDGET_MIN_INTERVAL_MS = 10_000L
        private const val RELOGIN_AFTER_MS = 10 * 60_000L
        const val ACTION_STOP = "ua.ecowi.action.STOP_LIVE"

        fun start(context: Context) {
            val i = Intent(context, LiveService::class.java)
            runCatching { context.startForegroundService(i) }
                .onFailure { Log.w(TAG, "cannot start live service now", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveService::class.java))
        }

        /** Start or stop according to settings (called on app start, boot, settings change). */
        fun sync(context: Context) {
            val s = AppSettings(context)
            if (s.liveMode && s.source == DataSourceKind.APP_ACCOUNT && s.account != null) start(context) else stop(context)
            RefreshScheduler.ensureScheduled(context)
        }
    }
}

/** Restarts live mode and background refresh after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> LiveService.sync(context)
        }
    }
}
