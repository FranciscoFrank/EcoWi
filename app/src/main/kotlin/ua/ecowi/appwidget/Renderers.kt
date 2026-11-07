package ua.ecowi.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import ua.ecowi.R
import ua.ecowi.core.BatteryBand
import ua.ecowi.core.LoadChannel
import ua.ecowi.core.LoadView

/** Bindings shared by every widget layout (views missing in a layout are simply ignored). */
internal object CommonBind {

    fun bind(context: Context, rv: RemoteViews, m: WidgetModel, p: Painter, widgetId: Int, kind: Widgets.Kind, socDp: Float) {
        p.background(R.id.bg)

        soc(context, rv, p, R.id.soc_img, m.socText, socDp)
        rv.setViewVisibility(R.id.charge_icon, if (m.charging) View.VISIBLE else View.GONE)
        p.tint(R.id.charge_icon) { it.high }
        p.tint(R.id.divider) { it.track }

        bindLoad(context, rv, m, p)
        rv.setOnClickPendingIntent(android.R.id.background, Widgets.clickIntent(context, widgetId, m.needsSetup, kind))
    }

    /** The percentage in Unbounded (see [SocText]); tinted like text, read out by TalkBack. */
    fun soc(context: Context, rv: RemoteViews, p: Painter, viewId: Int, text: String, sizeDp: Float) {
        rv.setImageViewBitmap(viewId, SocText.bitmap(context, text, sizeDp))
        rv.setContentDescription(viewId, text)
        p.tint(viewId) { it.text }
    }

    fun caption(rv: RemoteViews, p: Painter, text: String?) {
        rv.setViewVisibility(R.id.caption_text, if (text.isNullOrEmpty()) View.GONE else View.VISIBLE)
        if (text != null) rv.setTextViewText(R.id.caption_text, text)
        p.text(R.id.caption_text) { it.textSecondary }
    }

    /** Linear bar: level-scaled drawable, tinted at runtime (works on every API level). */
    fun bar(rv: RemoteViews, m: WidgetModel, p: Painter) {
        p.tint(R.id.bar_track) { it.track }
        p.tint(R.id.bar_fill) { it.band(m.band) }
        rv.setInt(R.id.bar_fill, "setImageLevel", (m.soc ?: 0).coerceIn(0, 100) * 100)
    }

    private fun bindLoad(context: Context, rv: RemoteViews, m: WidgetModel, p: Painter) {
        listOf(R.id.load_single_value, R.id.ac_value, R.id.dc_value).forEach { id -> p.text(id) { it.text } }
        listOf(R.id.load_single_label, R.id.ac_label, R.id.dc_label).forEach { id -> p.text(id) { it.textSecondary } }
        listOf(R.id.ac_icon, R.id.dc_icon).forEach { id -> p.tint(id) { it.accent } }

        when (val load = m.load) {
            is LoadView.Alternating -> {
                rv.setViewVisibility(R.id.load_single, View.GONE)
                rv.setViewVisibility(R.id.load_flipper, View.VISIBLE)
                rv.setTextViewText(R.id.ac_value, Format.watts(context, load.acWatts))
                rv.setTextViewText(R.id.dc_value, Format.watts(context, load.dcWatts))
                rv.setInt(R.id.load_flipper, "setFlipInterval", m.cycleMs)
            }
            is LoadView.Single -> {
                showSingle(rv)
                val ac = load.channel == LoadChannel.AC
                rv.setImageViewResource(R.id.load_single_icon, if (ac) R.drawable.ic_ac else R.drawable.ic_dc)
                p.tint(R.id.load_single_icon) { it.accent }
                rv.setTextViewText(R.id.load_single_value, Format.watts(context, load.watts))
                rv.setTextViewText(R.id.load_single_label, context.getString(if (ac) R.string.label_ac else R.string.label_dc))
            }
            LoadView.Idle -> {
                showSingle(rv)
                rv.setImageViewResource(R.id.load_single_icon, R.drawable.ic_idle)
                p.tint(R.id.load_single_icon) { it.textSecondary }
                rv.setTextViewText(
                    R.id.load_single_value,
                    if (m.hasData) Format.watts(context, 0) else context.getString(R.string.soc_unknown),
                )
                rv.setTextViewText(R.id.load_single_label, context.getString(R.string.label_idle))
            }
        }
    }

    private fun showSingle(rv: RemoteViews) {
        rv.setViewVisibility(R.id.load_flipper, View.GONE)
        rv.setViewVisibility(R.id.load_single, View.VISIBLE)
    }
}

/** 5×1 bar widget (also 2–4 columns and 2-row stacks). */
internal object BarRenderer {

    private enum class Caption { NONE, SHORT, FULL }

    fun render(context: Context, manager: AppWidgetManager, widgetId: Int, m: WidgetModel): RemoteViews =
        Widgets.sized(
            manager, widgetId,
            listOf(
                SizeF(110f, 40f) to { build(context, widgetId, R.layout.widget_compact, Caption.NONE, SocText.COMPACT, m) },
                SizeF(230f, 40f) to { build(context, widgetId, R.layout.widget_row, Caption.SHORT, SocText.ROW, m) },
                SizeF(230f, 150f) to { build(context, widgetId, R.layout.widget_tall, Caption.FULL, SocText.TALL, m) },
            ),
        )

    private fun build(
        context: Context,
        widgetId: Int,
        layout: Int,
        caption: Caption,
        socDp: Float,
        m: WidgetModel,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, layout)
        val p = Painter(context, rv, m.appearance)
        CommonBind.bind(context, rv, m, p, widgetId, Widgets.Kind.BAR, socDp)
        CommonBind.bar(rv, m, p)
        CommonBind.caption(
            rv, p,
            when (caption) {
                Caption.NONE -> null
                Caption.SHORT -> m.captionShort
                Caption.FULL -> m.captionFull
            },
        )
        return rv
    }
}

/** Ring widgets: 1×1 / 2×2 ring, 4×2 "batteries" (station + phone), 2×1 pill for lock screens. */
internal object RingRenderer {

    fun renderRing(context: Context, manager: AppWidgetManager, widgetId: Int, m: WidgetModel): RemoteViews =
        Widgets.sized(
            manager, widgetId,
            listOf(
                SizeF(40f, 40f) to { ring(context, manager, widgetId, R.layout.widget_ring_small, m, Widgets.Kind.RING) },
                SizeF(100f, 100f) to { ring(context, manager, widgetId, R.layout.widget_ring, m, Widgets.Kind.RING) },
                SizeF(220f, 100f) to { ring(context, manager, widgetId, R.layout.widget_ring_duo, m, Widgets.Kind.RING) },
            ),
        )

    fun renderMini(context: Context, manager: AppWidgetManager, widgetId: Int, m: WidgetModel): RemoteViews =
        Widgets.sized(
            manager, widgetId,
            listOf(
                SizeF(40f, 40f) to { ring(context, manager, widgetId, R.layout.widget_ring_small, m, Widgets.Kind.MINI) },
                SizeF(110f, 40f) to { ring(context, manager, widgetId, R.layout.widget_mini_pill, m, Widgets.Kind.MINI) },
            ),
        )

    private fun ring(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        layout: Int,
        m: WidgetModel,
        kind: Widgets.Kind,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, layout)
        val p = Painter(context, rv, m.appearance)
        val socDp = when (layout) {
            R.layout.widget_ring_small -> smallRingTextDp(manager, widgetId, m.socText)
            R.layout.widget_ring_duo -> SocText.DUO
            R.layout.widget_mini_pill -> SocText.PILL
            else -> SocText.RING
        }
        CommonBind.bind(context, rv, m, p, widgetId, kind, socDp)
        CommonBind.caption(rv, p, m.captionShort)

        val thick = layout == R.layout.widget_ring_small || layout == R.layout.widget_mini_pill
        rv.setImageViewBitmap(R.id.ring_track, RingBitmaps.ring(1f, thick))
        rv.setImageViewBitmap(R.id.ring_arc, RingBitmaps.ring((m.soc ?: 0) / 100f, thick))
        p.tint(R.id.ring_track) { it.track }
        p.tint(R.id.ring_arc) { it.band(m.band) }

        if (layout != R.layout.widget_ring_duo) return rv

        // "Batteries" layout: this phone next to the station.
        val phone = m.phone
        rv.setImageViewBitmap(R.id.phone_ring_track, RingBitmaps.ring(1f, thick))
        rv.setImageViewBitmap(R.id.phone_ring_arc, RingBitmaps.ring(phone.percent / 100f, thick))
        p.tint(R.id.phone_ring_track) { it.track }
        p.tint(R.id.phone_ring_arc) { it.band(BatteryBand.of(phone.percent)) }
        CommonBind.soc(context, rv, p, R.id.phone_soc_img, context.getString(R.string.soc_percent, phone.percent), SocText.DUO)
        rv.setViewVisibility(R.id.phone_charge_icon, if (phone.charging) View.VISIBLE else View.GONE)
        p.tint(R.id.phone_charge_icon) { it.high }
        listOf(R.id.station_label, R.id.phone_label).forEach { id -> p.text(id) { it.textSecondary } }
        return rv
    }
}

/**
 * Text size (dp, so the system font scale can't push it out of the ring) for the 1×1 ring:
 * ~80 % of the ring's inner diameter. The ring is the cell's shorter side minus 6dp margins, with a
 * 13 % stroke (thick ring). Widths are Unbounded SemiBold advances in em ("100%" ≈ 3.61 em).
 */
internal fun smallRingTextDp(manager: AppWidgetManager, widgetId: Int, text: String): Float {
    val o = manager.getAppWidgetOptions(widgetId)
    val sides = listOf(
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
        o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
    ).filter { it > 0 }
    val side = (sides.minOrNull() ?: 72).coerceIn(48, 120).toFloat()
    val inner = (side - 12f) * (1f - 2 * 0.13f)
    val em = textWidthEm(text)
    return (inner * 0.8f / em).coerceIn(9f, 28f)
}

/** Advance of [text] in Unbounded SemiBold, in em (measured from the font file). */
internal fun textWidthEm(text: String): Float =
    text.sumOf { c -> GLYPH_EM[c] ?: 0.9 }.toFloat().coerceAtLeast(1f)

private val GLYPH_EM = mapOf(
    '0' to 0.917, '1' to 0.507, '2' to 0.823, '3' to 0.830, '4' to 0.846, '5' to 0.799,
    '6' to 0.849, '7' to 0.740, '8' to 0.883, '9' to 0.849, '%' to 1.273, '—' to 1.193,
)

/**
 * Ring bitmaps are alpha masks (ALPHA_8, white = opaque) tinted per theme via setColorFilter,
 * so one bitmap serves light and dark. Round caps, start at 12 o'clock, clockwise.
 */
internal object RingBitmaps {
    private const val SIZE = 240
    private val cache = LruCache<Int, Bitmap>(24)

    fun ring(fraction: Float, thick: Boolean): Bitmap {
        val steps = (fraction.coerceIn(0f, 1f) * 200).toInt() // 0.5 % resolution
        val key = steps * 2 + if (thick) 1 else 0
        cache.get(key)?.let { return it }
        val stroke = SIZE * if (thick) 0.13f else 0.085f
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ALPHA_8)
        if (steps > 0) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = if (steps >= 200) Paint.Cap.BUTT else Paint.Cap.ROUND
                color = 0xFFFFFFFF.toInt()
            }
            val inset = stroke / 2f + 1f
            val oval = RectF(inset, inset, SIZE - inset, SIZE - inset)
            Canvas(bmp).drawArc(oval, -90f, steps / 200f * 360f, false, paint)
        }
        cache.put(key, bmp)
        return bmp
    }
}
