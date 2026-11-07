package ua.ecowi.core

/**
 * Whether the station itself answers, as opposed to the phone or EcoFlow's cloud.
 *
 * A station that is switched off, asleep or off Wi-Fi simply stops sending data, so there is no
 * "switched off" message to wait for. Instead every attempt to reach it is classified and fed to
 * [LinkDetector], which decides when silence has lasted long enough to tell the user.
 */
sealed interface LinkCheck {
    /** Fresh data arrived from the station. */
    data class Reached(val atMs: Long, val soc: Int?) : LinkCheck

    /** The cloud connection worked but the station didn't answer, or the cloud reports it offline. */
    data class Silent(val atMs: Long, val cloudSaysOffline: Boolean = false) : LinkCheck

    /** Nothing can be said about the station: the phone is offline, sign-in failed, another error. */
    data object Inconclusive : LinkCheck
}

data class LinkState(
    /** An "isn't responding" alert has been raised and the station hasn't answered since. */
    val down: Boolean = false,
    val silentChecks: Int = 0,
    /** When the station last answered; null = not since EcoWi started watching. */
    val lastSeenMs: Long? = null,
    val lastSoc: Int? = null,
)

sealed interface LinkEvent {
    data class Unreachable(val lastSeenMs: Long?, val lastSoc: Int?, val cloudSaysOffline: Boolean) : LinkEvent
    data class BackOnline(val soc: Int?, val downForMs: Long) : LinkEvent
}

object LinkDetector {
    /** Silent checks needed before alerting (one may be a Wi-Fi hiccup)… */
    const val SILENT_CHECKS = 2

    /** …and minimum time without data since the last answer. */
    const val MIN_SILENCE_MS = 3 * 60_000L

    fun process(state: LinkState, check: LinkCheck): Pair<LinkState, LinkEvent?> = when (check) {
        is LinkCheck.Reached -> {
            val downFor = (check.atMs - (state.lastSeenMs ?: check.atMs)).coerceAtLeast(0)
            val event = if (state.down) LinkEvent.BackOnline(check.soc, downFor) else null
            LinkState(lastSeenMs = check.atMs, lastSoc = check.soc ?: state.lastSoc) to event
        }

        is LinkCheck.Silent -> {
            val checks = state.silentChecks + 1
            val longEnough = state.lastSeenMs.let { it == null || check.atMs - it >= MIN_SILENCE_MS }
            val goesDown = !state.down && longEnough && (check.cloudSaysOffline || checks >= SILENT_CHECKS)
            val event = if (goesDown) LinkEvent.Unreachable(state.lastSeenMs, state.lastSoc, check.cloudSaysOffline) else null
            state.copy(silentChecks = checks, down = state.down || goesDown) to event
        }

        LinkCheck.Inconclusive -> state to null
    }
}
