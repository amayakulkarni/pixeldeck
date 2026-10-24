package dev.pixeldeck

import dev.pixeldeck.alarm.AlarmMath
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmMathTest {
    // 2026-10-24 is a Saturday.
    private val saturdayNoon = LocalDateTime.of(2026, 10, 24, 12, 0)

    @Test fun oneOffLaterTodayRingsToday() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 18, 30), AlarmMath.nextTrigger(18, 30, 0, saturdayNoon))
    }

    @Test fun oneOffAlreadyPassedRingsTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 25, 7, 0), AlarmMath.nextTrigger(7, 0, 0, saturdayNoon))
    }

    @Test fun exactlyNowIsNotInTheFuture() {
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), AlarmMath.nextTrigger(12, 0, 0, saturdayNoon))
    }

    @Test fun weekdaysSkipTheWeekend() {
        // Saturday noon, 07:00 on weekdays -> Monday 26th.
        assertEquals(LocalDateTime.of(2026, 10, 26, 7, 0), AlarmMath.nextTrigger(7, 0, AlarmMath.WEEKDAYS, saturdayNoon))
    }

    @Test fun singleDayOfWeekWrapsToNextWeek() {
        val saturdayOnly = 1 shl 5
        assertEquals(LocalDateTime.of(2026, 10, 31, 9, 0), AlarmMath.nextTrigger(9, 0, saturdayOnly, saturdayNoon))
    }

    @Test fun sameDayRepeatStillTodayWhenTimeIsAhead() {
        val saturdayOnly = 1 shl 5
        assertEquals(LocalDateTime.of(2026, 10, 24, 20, 0), AlarmMath.nextTrigger(20, 0, saturdayOnly, saturdayNoon))
    }

    @Test fun describeDays() {
        assertEquals("Once", AlarmMath.describeDays(0))
        assertEquals("Every day", AlarmMath.describeDays(AlarmMath.EVERY_DAY))
        assertEquals("Weekdays", AlarmMath.describeDays(AlarmMath.WEEKDAYS))
        assertEquals("Weekends", AlarmMath.describeDays(AlarmMath.WEEKEND))
        assertEquals("Mon, Wed", AlarmMath.describeDays(0b0000101))
    }
}
