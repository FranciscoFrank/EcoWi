package ua.ecowi.appwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import ua.ecowi.R
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The charge percentage drawn in Unbounded as an alpha-mask bitmap.
 *
 * Launchers inflate widgets in their own process and don't load app fonts for RemoteViews, so a
 * TextView with `android:fontFamily="@font/…"` falls back to the system font. Drawing the text here and
 * showing it in an ImageView keeps the widget identical to the app. Like the rings, the bitmap is
 * ALPHA_8 and tinted per theme with a colour filter, so one bitmap serves light and dark.
 */
internal object SocText {

    /** Text sizes (dp) per layout; the 1×1 ring uses [smallRingTextDp] instead. */
    const val ROW = 22f
    const val COMPACT = 17f
    const val TALL = 34f
    const val RING = 22f
    const val DUO = 18f
    const val PILL = 17f

    private val cache = LruCache<String, Bitmap>(24)

    @Volatile
    private var unbounded: Typeface? = null

    fun bitmap(context: Context, text: String, sizeDp: Float): Bitmap {
        val dm = context.resources.displayMetrics
        val px = sizeDp * dm.density
        val key = "$text|${px.roundToInt()}"
        cache.get(key)?.let { return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = typeface(context)
            textSize = px
            color = Color.WHITE
        }
        // Same box as a TextView with includeFontPadding=false: ascent to descent.
        val fm = paint.fontMetrics
        val pad = 1f
        val width = (ceil(paint.measureText(text) + 2 * pad)).toInt().coerceAtLeast(1)
        val height = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        bmp.density = dm.densityDpi
        Canvas(bmp).drawText(text, pad, -fm.ascent, paint)
        cache.put(key, bmp)
        return bmp
    }

    private fun typeface(context: Context): Typeface =
        unbounded ?: (runCatching { context.resources.getFont(R.font.unbounded_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD)
            .also { unbounded = it }
}
