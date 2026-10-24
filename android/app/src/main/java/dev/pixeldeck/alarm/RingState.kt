package dev.pixeldeck.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What is ringing right now (null = silence). Observed by the dashboard overlay and the lock-screen ring activity. */
data class RingInfo(val kind: String, val id: String, val label: String, val startedAt: Long) {
    val isTimer get() = kind == KIND_TIMER
    val title get() = label.ifBlank { if (isTimer) "Timer" else "Alarm" }

    companion object {
        const val KIND_TIMER = "timer"
        const val KIND_ALARM = "alarm"
    }
}

object RingState {
    private val _current = MutableStateFlow<RingInfo?>(null)
    val current: StateFlow<RingInfo?> = _current

    fun set(info: RingInfo?) { _current.value = info }
}
