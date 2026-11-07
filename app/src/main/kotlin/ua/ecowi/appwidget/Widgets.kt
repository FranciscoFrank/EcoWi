package ua.ecowi.appwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import ua.ecowi.data.AppSettings
import ua.ecowi.data.SnapshotCache
import ua.ecowi.ui.MainActivity
import ua.ecowi.work.RefreshScheduler

/** Registry of the app's widget types and shared rendering plumbing. */
object Widgets {

    enum class Kind(val provider: Class<out AppWidgetProvider>) {
        /** 5×1 bar (resizable; works in One UI stacks). */
        BAR(PowerWidgetProvider::class.java),

        /** 2×2 ring; 4×2 shows the station next to this phone ("batteries"). */
        RING(RingWidgetProvider::class.java),

        /** 1×1 ring / 2×1 pill — small enough for lock-screen widget areas. */
        MINI(MiniWidgetProvider::class.java),
    }

    const val ACTION_REFRESH = "ua.ecowi.action.REFRESH"

    fun ids(context: Context, kind: Kind): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, kind.provider))

    fun hasWidgets(context: Context): Boolean = Kind.entries.any { ids(context, it).isNotEmpty() }

    fun kindOf(context: Context, widgetId: Int): Kind? {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return null
        return Kind.entries.firstOrNull { it.provider.name == info.provider.className }
    }

    fun renderAll(context: Context, refreshing: Boolean = false) {
        val manager = AppWidgetManager.getInstance(context)
        for (kind in Kind.entries) ids(context, kind).forEach { render(context, manager, it, kind, refreshing) }
    }

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int, kind: Kind, refreshing: Boolean = false) {
        val model = WidgetModels.build(context, widgetId, refreshing)
        val views = when (kind) {
            Kind.BAR -> BarRenderer.render(context, manager, widgetId, model)
            Kind.RING -> RingRenderer.renderRing(context, manager, widgetId, model)
            Kind.MINI -> RingRenderer.renderMini(context, manager, widgetId, model)
        }
        manager.updateAppWidget(widgetId, views)
    }

    /**
     * Responsive RemoteViews. Android 12+: the launcher picks the best variant itself
     * (also inside One UI stacks). Older: pick from the widget's current size options.
     */
    fun sized(
        manager: AppWidgetManager,
        widgetId: Int,
        variants: List<Pair<SizeF, () -> RemoteViews>>,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            return RemoteViews(variants.associate { (size, build) -> size to build() })
        }
        val o: Bundle = manager.getAppWidgetOptions(widgetId)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toFloat()
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).toFloat()
        val fitting = variants.filter { (s, _) -> s.width <= w && s.height <= h }
        val chosen = fitting.maxByOrNull { (s, _) -> s.width * s.height } ?: variants.first()
        return chosen.second()
    }

    fun clickIntent(context: Context, widgetId: Int, needsSetup: Boolean, kind: Kind): PendingIntent =
        if (needsSetup) {
            PendingIntent.getActivity(
                context, widgetId,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            PendingIntent.getBroadcast(
                context, widgetId,
                Intent(context, kind.provider).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
}

/** Shared behaviour of all EcoWi widget providers. */
abstract class BaseWidgetProvider(private val kind: Widgets.Kind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Widgets.render(context, manager, it, kind) }
        RefreshScheduler.ensureScheduled(context)
        // One UI calls onUpdate on placement, restore, stack edits… only hit the API if data is old.
        val settings = AppSettings(context)
        val last = SnapshotCache(context).load().lastAttemptMs
        if (!settings.liveMode && System.currentTimeMillis() - last > REFRESH_ON_UPDATE_AFTER_MS) {
            RefreshScheduler.refreshNow(context)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Widgets.render(context, manager, appWidgetId, kind)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_REFRESH) {
            Widgets.renderAll(context, refreshing = true)
            RefreshScheduler.refreshNow(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = AppSettings(context)
        appWidgetIds.forEach(settings::removeWidget)
    }

    override fun onEnabled(context: Context) = RefreshScheduler.ensureScheduled(context)

    override fun onDisabled(context: Context) {
        // Called per provider: keep background work while other widgets or live mode remain.
        if (!Widgets.hasWidgets(context) && !AppSettings(context).liveMode) RefreshScheduler.cancelAll(context)
    }

    private companion object {
        const val REFRESH_ON_UPDATE_AFTER_MS = 5 * 60_000L
    }
}

class PowerWidgetProvider : BaseWidgetProvider(Widgets.Kind.BAR)
class RingWidgetProvider : BaseWidgetProvider(Widgets.Kind.RING)
class MiniWidgetProvider : BaseWidgetProvider(Widgets.Kind.MINI)
