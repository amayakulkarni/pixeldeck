package dev.pixeldeck.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pixeldeck.ui.PixelDeckTheme
import dev.pixeldeck.ui.RingCard

/** Full-screen "alarm is ringing" screen that shows over the lock screen. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            PixelDeckTheme {
                val info by RingState.current.collectAsStateWithLifecycle()
                LaunchedEffect(info) { if (info == null) finish() }
                info?.let { RingCard(it) }
            }
        }
    }
}
