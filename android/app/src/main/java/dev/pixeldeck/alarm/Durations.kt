package dev.pixeldeck.alarm

/** Parsing and formatting of timer durations. Pure Kotlin so it is unit tested on the JVM. */
object Durations {
    private val PART = Regex("""(\d+)\s*([hms])""", RegexOption.IGNORE_CASE)
    private const val MAX_MS = 99L * 3_600_000

    /**
     * Accepts "90" (minutes), "10m", "45s", "1h30m", "1h 30m 15s", "05:30" (mm:ss) and "1:05:30" (h:mm:ss).
     * Returns milliseconds, or null if the text is not a positive duration.
     */
    fun parse(text: String): Long? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        val ms: Long = when {
            t.all { it.isDigit() } -> if (t.length > 6) return null else t.toLong() * 60_000
            ':' in t -> {
                val nums = t.split(':').map { it.trim().toLongOrNull() ?: return null }
                when (nums.size) {
                    2 -> nums[0] * 60_000 + nums[1] * 1_000
                    3 -> nums[0] * 3_600_000 + nums[1] * 60_000 + nums[2] * 1_000
                    else -> return null
                }
            }
            else -> {
                val parts = PART.findAll(t).toList()
                if (parts.isEmpty()) return null
                // Everything except the matched parts must be whitespace, so "10 minutes" or "5x" are rejected.
                if (PART.replace(t, "").isNotBlank()) return null
                parts.sumOf {
                    val n = it.groupValues[1].toLongOrNull() ?: return null
                    when (it.groupValues[2]) {
                        "h" -> n * 3_600_000
                        "m" -> n * 60_000
                        else -> n * 1_000
                    }
                }
            }
        }
        return if (ms in 1..MAX_MS) ms else null
    }

    /** "5:07" or "1:05:07" for a countdown; rounds up so a timer never shows 0:00 while still running. */
    fun format(ms: Long): String {
        val totalSeconds = (maxOf(ms, 0L) + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "01:05.23" for the stopwatch. */
    fun formatStopwatch(ms: Long): String {
        val total = maxOf(ms, 0L)
        val centis = (total / 10) % 100
        val s = (total / 1000) % 60
        val m = total / 60_000
        return "%02d:%02d.%02d".format(m, s, centis)
    }

    /** Short human text such as "10 min" or "1 h 30 min" for list rows. */
    fun describe(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0 || (h == 0L && m == 0L)) add("$s s")
        }.joinToString(" ")
    }
}
