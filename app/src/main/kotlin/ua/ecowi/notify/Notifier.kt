package ua.ecowi.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ua.ecowi.R
import ua.ecowi.appwidget.Format
import ua.ecowi.core.BatteryBand
import ua.ecowi.core.Port
import ua.ecowi.core.PowerSnapshot
import ua.ecowi.core.StationEvent
import ua.ecowi.ui.MainActivity

/**
 * All notifications of the app:
 *  - alert channels (battery / power / ports) for [StationEvent]s;
 *  - the "live" ongoing notification. On Android 16+ it is a Live Update (ProgressStyle +
 *    promoted ongoing), which One UI 8 shows in the Now Bar on the lock screen.
 */
object Notifier {

    const val CH_LIVE = "live"
    const val CH_BATTERY = "battery"
    const val CH_POWER = "power"
    const val CH_PORTS = "ports"

    const val ID_LIVE = 1
    private const val ID_LOW = 10
    private const val ID_FULL = 11
    private const val ID_CHARGING = 12
    private const val ID_GRID = 13
    private const val ID_PORT_BASE = 20

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CH_LIVE, context.getString(R.string.ch_live), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.ch_live_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_BATTERY, context.getString(R.string.ch_battery), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ch_battery_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_POWER, context.getString(R.string.ch_power), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.ch_power_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_PORTS, context.getString(R.string.ch_ports), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.ch_ports_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannels(channels)
    }

    fun canNotify(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    // ------------------------------------------------------------------ alerts

    fun post(context: Context, events: List<StationEvent>, snap: PowerSnapshot) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        for (e in events) {
            val (id, channel, title, text) = describe(context, e, snap)
            val n = Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_ecowi)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setColor(context.getColor(R.color.ew_brand))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(if (channel == CH_PORTS) Notification.CATEGORY_STATUS else Notification.CATEGORY_EVENT)
                .setContentIntent(openApp(context))
                .build()
            nm.notify(id, n)
        }
    }

    private data class Alert(val id: Int, val channel: String, val title: String, val text: String)

    private fun describe(c: Context, e: StationEvent, s: PowerSnapshot): Alert {
        val remain = s.dischargeRemainMin?.let { Format.duration(c, it) }
        return when (e) {
            is StationEvent.BatteryLow -> Alert(
                ID_LOW, CH_BATTERY,
                c.getString(if (e.threshold <= 5) R.string.n_low_critical_title else R.string.n_low_title, e.soc),
                listOfNotNull(
                    c.getString(if (e.threshold <= 5) R.string.n_low_critical_text else R.string.n_low_text),
                    remain?.let { c.getString(R.string.n_remaining, it) },
                ).joinToString(" "),
            )
            is StationEvent.BatteryFull -> Alert(
                ID_FULL, CH_BATTERY,
                if (e.soc >= 100) c.getString(R.string.n_full_title)
                else c.getString(R.string.n_full_limit_title, e.soc),
                c.getString(R.string.n_full_text),
            )
            is StationEvent.ChargingStarted -> Alert(
                ID_CHARGING, CH_POWER,
                c.getString(R.string.n_charging_title),
                e.inputW?.takeIf { it > 0 }?.let { c.getString(R.string.n_charging_text, Format.watts(c, it)) }
                    ?: c.getString(R.string.n_charging_text_plain),
            )
            is StationEvent.GridRestored -> Alert(
                ID_GRID, CH_POWER,
                c.getString(R.string.n_grid_on_title),
                c.getString(if (e.charging) R.string.n_grid_on_text_charging else R.string.n_grid_on_text),
            )
            StationEvent.GridLost -> Alert(
                ID_GRID, CH_POWER,
                c.getString(R.string.n_grid_off_title),
                listOfNotNull(
                    c.getString(R.string.n_grid_off_text, s.socPercent ?: 0),
                    remain?.let { c.getString(R.string.n_remaining, it) },
                ).joinToString(" "),
            )
            is StationEvent.PortConnected -> Alert(
                ID_PORT_BASE + e.port.ordinal, CH_PORTS,
                c.getString(if (e.port == Port.AC) R.string.n_port_ac_title else R.string.n_port_title, Format.portName(c, e.port)),
                c.getString(R.string.n_port_text, Format.watts(c, e.watts)),
            )
        }
    }

    // ------------------------------------------------------------------ live notification

    /** Ongoing status for the live service; a Live Update (Now Bar) on Android 16+. */
    fun live(context: Context, snap: PowerSnapshot?, connected: Boolean): Notification {
        ensureChannels(context)
        val soc = snap?.socPercent
        val title = when {
            soc == null -> context.getString(R.string.live_title_waiting)
            snap.isCharging -> context.getString(R.string.live_title_charging, soc)
            else -> context.getString(R.string.live_title, soc)
        }
        val text = snap?.let { liveText(context, it) } ?: context.getString(R.string.live_text_connecting)
        val subText = if (connected) null else context.getString(R.string.live_offline)

        val b = Notification.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_ecowi)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
        subText?.let { b.setSubText(it) }

        if (Build.VERSION.SDK_INT >= 36 && soc != null) {
            b.setStyle(
                Notification.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100).setColor(bandColor(context, soc))))
                    .setProgress(soc),
            )
            b.setShortCriticalText("$soc%")
            b.setRequestPromotedOngoing(true)
        } else if (soc != null) {
            b.setProgress(100, soc, false)
            b.setColor(bandColor(context, soc))
        }
        return b.build()
    }

    private fun liveText(c: Context, s: PowerSnapshot): String = buildList {
        s.acOutW?.takeIf { it >= 2 }?.let { add(c.getString(R.string.live_ac, Format.watts(c, it))) }
        s.dcOutW?.takeIf { it >= 2 }?.let { add(c.getString(R.string.live_dc, Format.watts(c, it))) }
        if (s.isCharging) s.totalInW?.takeIf { it > 0 }?.let { add("+" + Format.watts(c, it)) }
        val remain = if (s.isCharging) s.chargeRemainMin else s.dischargeRemainMin
        remain?.let { add(Format.duration(c, it)) }
        if (isEmpty()) add(c.getString(R.string.live_idle))
    }.joinToString(" · ")

    private fun bandColor(c: Context, soc: Int): Int = c.getColor(
        when (BatteryBand.of(soc)) {
            BatteryBand.CRITICAL -> R.color.ew_batt_critical
            BatteryBand.LOW -> R.color.ew_batt_low
            BatteryBand.MEDIUM -> R.color.ew_batt_medium
            else -> R.color.ew_batt_high
        },
    )

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
