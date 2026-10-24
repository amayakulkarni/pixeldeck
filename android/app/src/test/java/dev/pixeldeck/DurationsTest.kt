package dev.pixeldeck

import dev.pixeldeck.alarm.Durations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationsTest {
    @Test fun parsesUnits() {
        assertEquals(600_000L, Durations.parse("10m"))
        assertEquals(45_000L, Durations.parse("45s"))
        assertEquals(5_400_000L, Durations.parse("1h30m"))
        assertEquals(5_415_000L, Durations.parse("1h 30m 15s"))
        assertEquals(600_000L, Durations.parse("10M"))
    }

    @Test fun bareNumberIsMinutes() {
        assertEquals(5 * 60_000L, Durations.parse("5"))
        assertEquals(90 * 60_000L, Durations.parse(" 90 "))
    }

    @Test fun parsesClockStyle() {
        assertEquals(330_000L, Durations.parse("05:30"))
        assertEquals(3_930_000L, Durations.parse("1:05:30"))
    }

    @Test fun rejectsGarbage() {
        assertNull(Durations.parse(""))
        assertNull(Durations.parse("abc"))
        assertNull(Durations.parse("10 minutes"))
        assertNull(Durations.parse("0"))
        assertNull(Durations.parse("0m"))
        assertNull(Durations.parse("1:2:3:4"))
        assertNull(Durations.parse("99999999"))
        assertNull(Durations.parse("5x"))
    }

    @Test fun formatsCountdown() {
        assertEquals("5:07", Durations.format(307_000))
        assertEquals("1:05:07", Durations.format(3_907_000))
        assertEquals("0:01", Durations.format(1))      // rounds up
        assertEquals("0:00", Durations.format(-50))
    }

    @Test fun formatsStopwatchAndDescription() {
        assertEquals("01:05.23", Durations.formatStopwatch(65_230))
        assertEquals("10 min", Durations.describe(600_000))
        assertEquals("1 h 30 min", Durations.describe(5_400_000))
        assertEquals("45 s", Durations.describe(45_000))
    }
}
