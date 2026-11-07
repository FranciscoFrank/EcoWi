package ua.ecowi.appwidget

import android.content.Context
import ua.ecowi.R
import ua.ecowi.core.Port
import java.util.Locale

/** Localised formatting shared by widgets, notifications and the app UI. */
object Format {

    fun watts(context: Context, watts: Int): String =
        if (watts >= 1000) {
            context.getString(R.string.kilowatts, String.format(Locale.getDefault(), "%.1f", watts / 1000.0))
        } else {
            context.getString(R.string.watts, watts)
        }

    fun duration(context: Context, minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) context.getString(R.string.duration_hm, h, m) else context.getString(R.string.duration_m, m)
    }

    fun portName(context: Context, port: Port): String = context.getString(
        when (port) {
            Port.AC -> R.string.port_ac
            Port.USB_A1 -> R.string.port_usb_a1
            Port.USB_A2 -> R.string.port_usb_a2
            Port.USB_C1 -> R.string.port_usb_c1
            Port.USB_C2 -> R.string.port_usb_c2
            Port.DC_12V -> R.string.port_12v
        },
    )
}
