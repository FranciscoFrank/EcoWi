package ua.ecowi.appwidget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import ua.ecowi.R
import ua.ecowi.core.BatteryBand
import ua.ecowi.data.WidgetAppearance
import ua.ecowi.data.WidgetTheme

/** Colours for one widget theme. */
data class Palette(
    val bg: Int,
    val text: Int,
    val textSecondary: Int,
    val track: Int,
    val accent: Int,
    val high: Int,
    val medium: Int,
    val low: Int,
    val critical: Int,
) {
    fun band(band: BatteryBand?): Int = when (band) {
        BatteryBand.CRITICAL -> critical
        BatteryBand.LOW -> low
        BatteryBand.MEDIUM -> medium
        BatteryBand.HIGH, null -> high
    }

    companion object {
        /** EcoWi palette: graphite + volt green; or Material You neutrals when [dynamic]. */
        fun light(c: Context, dynamic: Boolean): Palette = if (dynamic && Build.VERSION.SDK_INT >= 31) {
            Palette(
                bg = c.getColor(android.R.color.system_neutral1_50),
                text = c.getColor(android.R.color.system_neutral1_900),
                textSecondary = c.getColor(android.R.color.system_neutral2_600),
                track = c.getColor(android.R.color.system_neutral2_100),
                accent = c.getColor(android.R.color.system_accent1_600),
                high = c.getColor(R.color.ew_batt_high_light), medium = c.getColor(R.color.ew_batt_medium_light),
                low = c.getColor(R.color.ew_batt_low_light), critical = c.getColor(R.color.ew_batt_critical_light),
            )
        } else {
            Palette(
                bg = c.getColor(R.color.ew_w_bg_light), text = c.getColor(R.color.ew_w_text_light),
                textSecondary = c.getColor(R.color.ew_w_text2_light), track = c.getColor(R.color.ew_w_track_light),
                accent = c.getColor(R.color.ew_w_accent_light),
                high = c.getColor(R.color.ew_batt_high_light), medium = c.getColor(R.color.ew_batt_medium_light),
                low = c.getColor(R.color.ew_batt_low_light), critical = c.getColor(R.color.ew_batt_critical_light),
            )
        }

        fun dark(c: Context, dynamic: Boolean): Palette = if (dynamic && Build.VERSION.SDK_INT >= 31) {
            Palette(
                bg = c.getColor(android.R.color.system_neutral1_900),
                text = c.getColor(android.R.color.system_neutral1_50),
                textSecondary = c.getColor(android.R.color.system_neutral2_300),
                track = c.getColor(android.R.color.system_neutral2_800),
                accent = c.getColor(android.R.color.system_accent1_200),
                high = c.getColor(R.color.ew_batt_high_dark), medium = c.getColor(R.color.ew_batt_medium_dark),
                low = c.getColor(R.color.ew_batt_low_dark), critical = c.getColor(R.color.ew_batt_critical_dark),
            )
        } else {
            Palette(
                bg = c.getColor(R.color.ew_w_bg_dark), text = c.getColor(R.color.ew_w_text_dark),
                textSecondary = c.getColor(R.color.ew_w_text2_dark), track = c.getColor(R.color.ew_w_track_dark),
                accent = c.getColor(R.color.ew_w_accent_dark),
                high = c.getColor(R.color.ew_batt_high_dark), medium = c.getColor(R.color.ew_batt_medium_dark),
                low = c.getColor(R.color.ew_batt_low_dark), critical = c.getColor(R.color.ew_batt_critical_dark),
            )
        }
    }
}

/**
 * Applies colours to RemoteViews for a widget's [WidgetAppearance].
 *
 * Theme SYSTEM on Android 12+: both palettes are sent and the launcher picks day/night at
 * render time (RemoteViews.setColorInt), so switching dark mode re-colours instantly.
 * LIGHT / DARK: one fixed palette, regardless of the device theme.
 */
class Painter(context: Context, private val rv: RemoteViews, val appearance: WidgetAppearance) {

    private val light = Palette.light(context, appearance.dynamicColor)
    private val dark = Palette.dark(context, appearance.dynamicColor)
    private val followSystem = appearance.theme == WidgetTheme.SYSTEM && Build.VERSION.SDK_INT >= 31

    /** The palette used when colours can't vary with night mode (and for bitmaps). */
    val current: Palette = when (appearance.theme) {
        WidgetTheme.LIGHT -> light
        WidgetTheme.DARK -> dark
        WidgetTheme.SYSTEM -> if (isNight(context)) dark else light
    }

    fun color(viewId: Int, method: String, pick: (Palette) -> Int) {
        if (followSystem && Build.VERSION.SDK_INT >= 31) {
            rv.setColorInt(viewId, method, pick(light), pick(dark))
        } else {
            rv.setInt(viewId, method, pick(current))
        }
    }

    fun text(viewId: Int, pick: (Palette) -> Int) = color(viewId, "setTextColor", pick)

    fun tint(viewId: Int, pick: (Palette) -> Int) = color(viewId, "setColorFilter", pick)

    /** Background layer: colour + user opacity. */
    fun background(viewId: Int) {
        tint(viewId) { it.bg }
        rv.setInt(viewId, "setImageAlpha", (appearance.backgroundOpacity.coerceIn(0, 100) * 255) / 100)
    }

    companion object {
        fun isNight(c: Context): Boolean =
            (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
