package ua.ecowi.core

/** How a remaining time is shown: minutes under an hour, hours + minutes under a day, then days + hours. */
sealed interface DurationParts {
    data class Minutes(val minutes: Int) : DurationParts
    data class HoursMinutes(val hours: Int, val minutes: Int) : DurationParts
    data class DaysHours(val days: Int, val hours: Int) : DurationParts

    companion object {
        fun of(totalMinutes: Int): DurationParts {
            val m = totalMinutes.coerceAtLeast(0)
            return when {
                m < 60 -> Minutes(m)
                m < 24 * 60 -> HoursMinutes(m / 60, m % 60)
                else -> {
                    val hours = (m + 30) / 60 // round to the nearest hour; minutes don't matter at this range
                    DaysHours(hours / 24, hours % 24)
                }
            }
        }
    }
}
