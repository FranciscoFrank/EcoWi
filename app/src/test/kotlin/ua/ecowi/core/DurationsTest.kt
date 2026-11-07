package ua.ecowi.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationsTest {
    @Test
    fun minutesUnderAnHour() = assertEquals(DurationParts.Minutes(45), DurationParts.of(45))

    @Test
    fun hoursAndMinutesUnderADay() = assertEquals(DurationParts.HoursMinutes(6, 10), DurationParts.of(6 * 60 + 10))

    @Test
    fun daysAndHoursFromADay() {
        // 61 h 59 min (seen on a real station at 0 W) rounds to 62 h = 2 days 14 hours
        assertEquals(DurationParts.DaysHours(2, 14), DurationParts.of(61 * 60 + 59))
        assertEquals(DurationParts.DaysHours(1, 0), DurationParts.of(24 * 60))
    }

    @Test
    fun negativeIsZero() = assertEquals(DurationParts.Minutes(0), DurationParts.of(-5))
}
