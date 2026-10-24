package dev.pixeldeck.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Pure alarm scheduling maths (no Android classes), unit tested on the JVM. */
object AlarmMath {
    /** Bit for a day: Monday = bit 0 ... Sunday = bit 6. */
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val EVERY_DAY = 0b1111111

    /**
     * The first moment strictly after [from] at [hour]:[minute] on a day allowed by [daysMask]
     * (0 = any day, i.e. a one-off alarm).
     */
    fun nextTrigger(hour: Int, minute: Int, daysMask: Int, from: LocalDateTime): LocalDateTime {
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        for (offset in 0..7) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            val candidate = LocalDateTime.of(date, time)
            if (!candidate.isAfter(from)) continue
            if (daysMask == 0 || (daysMask and bit(date.dayOfWeek)) != 0) return candidate
        }
        return LocalDateTime.of(from.toLocalDate().plusDays(1), time)
    }

    fun toEpochMillis(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    private val SHORT_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun describeDays(mask: Int): String = when (mask and EVERY_DAY) {
        0 -> "Once"
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKEND -> "Weekends"
        else -> SHORT_DAYS.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }.joinToString(", ")
    }
}
