package ua.ecowi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Tells "the phone is offline" apart from "the station doesn't answer". */
object Net {

    /** A network with validated internet access is active. */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** DNS, connect or timeout failure anywhere in the cause chain (Paho wraps them in MqttException). */
    fun isNetworkError(t: Throwable): Boolean =
        generateSequence(t) { it.cause }.take(10).any {
            it is java.net.UnknownHostException ||
                it is java.net.ConnectException ||
                it is java.net.SocketTimeoutException ||
                it is java.net.NoRouteToHostException ||
                it is java.net.PortUnreachableException
        }
}
