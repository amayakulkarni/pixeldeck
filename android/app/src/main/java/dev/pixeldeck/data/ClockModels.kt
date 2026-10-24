package dev.pixeldeck.data

import kotlinx.serialization.Serializable

/** A running countdown. Absolute [endAt] (epoch millis) so it survives app restarts. */
@Serializable
data class TimerItem(
    val id: String,
    val label: String = "",
    val durationMs: Long,
    val endAt: Long,
)

/**
 * A clock alarm. [days] is a bit mask, bit 0 = Monday ... bit 6 = Sunday; 0 means "once" (ring at
 * the next occurrence of the time, then switch off).
 */
@Serializable
data class AlarmItem(
    val id: String,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
)
