package ua.ecowi.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ua.ecowi.appwidget.Widgets
import ua.ecowi.core.LinkDetector
import ua.ecowi.data.AppSettings
import ua.ecowi.data.FailureKind
import ua.ecowi.data.FetchResult
import ua.ecowi.data.LinkStore
import ua.ecowi.data.StationRepository
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = StationRepository(applicationContext).refreshAndStore() // also raises alerts
        Widgets.renderAll(applicationContext)
        // A single silent check isn't enough to alert (Wi-Fi hiccups happen), so check again soon
        // instead of waiting for the next periodic run.
        if (result is FetchResult.Failed && result.kind == FailureKind.STATION_SILENT &&
            !LinkStore(applicationContext).load().down && !inputData.getBoolean(KEY_RECHECK, false)
        ) {
            RefreshScheduler.recheckSoon(applicationContext)
        }
        if (inputData.getBoolean(KEY_CHAINED, false)) {
            RefreshScheduler.scheduleShortChain(applicationContext, fromChain = true)
        }
        // Failures are shown on the widget; no WorkManager retries (they'd fight the schedule).
        return Result.success()
    }

    companion object {
        const val KEY_CHAINED = "chained"
        const val KEY_RECHECK = "recheck"
    }
}

/**
 * WorkManager's periodic minimum is 15 min. For 5/10 min we additionally chain one-off
 * requests with an initial delay. Under Doze both get deferred — that's Android, not a bug.
 * In live mode the service delivers data; the periodic job stays as a safety net.
 */
object RefreshScheduler {
    private const val PERIODIC = "ecowi-periodic"
    private const val CHAIN = "ecowi-chain"
    private const val NOW = "ecowi-now"
    private const val RECHECK = "ecowi-recheck"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensureScheduled(context: Context) {
        val settings = AppSettings(context)
        if (!settings.isConfigured) return
        val interval = if (settings.liveMode) 30 else settings.refreshIntervalMin
        val periodic = PeriodicWorkRequestBuilder<RefreshWorker>(maxOf(15, interval).toLong(), TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
        scheduleShortChain(context, fromChain = false)
    }

    /** No network constraint on purpose: offline should show up as an error, not a forever "Updating…". */
    /** One more attempt a few minutes after the station didn't answer (see LinkDetector). */
    fun recheckSoon(context: Context) {
        if (AppSettings(context).liveMode) return // the live connection has its own watchdog
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(LinkDetector.MIN_SILENCE_MS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(RefreshWorker.KEY_RECHECK to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RECHECK, ExistingWorkPolicy.KEEP, request)
    }

    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * @param fromChain true when called from the chained worker itself: APPEND makes the next
     * request run after the current one finishes (REPLACE would cancel the running worker).
     */
    fun scheduleShortChain(context: Context, fromChain: Boolean) {
        val wm = WorkManager.getInstance(context)
        val settings = AppSettings(context)
        val interval = settings.refreshIntervalMin
        if (settings.liveMode || interval >= 15 || !Widgets.hasWidgets(context)) {
            if (!fromChain) wm.cancelUniqueWork(CHAIN)
            return
        }
        val next = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(network)
            .setInputData(workDataOf(RefreshWorker.KEY_CHAINED to true))
            .build()
        val policy = if (fromChain) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        wm.enqueueUniqueWork(CHAIN, policy, next)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(CHAIN)
            cancelUniqueWork(NOW)
        }
    }
}
