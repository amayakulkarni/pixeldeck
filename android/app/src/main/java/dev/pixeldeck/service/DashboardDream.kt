package dev.pixeldeck.service

import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.dreams.DreamService
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.data.Settings
import dev.pixeldeck.ui.DashboardScreen
import dev.pixeldeck.ui.LocalLock
import dev.pixeldeck.ui.LockState
import dev.pixeldeck.ui.PixelDeckTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * PixelDeck as an Android screen saver ("Daydream"). Android starts a screen saver when the screen
 * times out while charging, and it is drawn above the lock screen, so this is how the dashboard
 * shows up on a locked phone sitting on a wireless charger.
 *
 * Enable it under Settings > Display > Screen saver > PixelDeck, "When to start": While charging.
 * With "only on wireless charger" enabled (default) it ends itself immediately on a cable.
 */
class DashboardDream : DreamService() {
    private val owner = DreamOwner()
    private var composeStarted = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val app = PixelDeckApp.instance
        val settings = runBlocking { withTimeoutOrNull(1_000) { app.store.settings.first() } } ?: Settings()

        if (settings.dreamWirelessOnly && !isChargingWirelessly()) {
            finish()
            return
        }

        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        // Compose needs lifecycle and saved-state owners on the view tree, which a DreamService does not provide.
        window.decorView.setViewTreeLifecycleOwner(owner)
        window.decorView.setViewTreeSavedStateRegistryOwner(owner)
        owner.moveTo(Lifecycle.State.RESUMED)
        composeStarted = true

        val keyguard = getSystemService(KeyguardManager::class.java)
        setContentView(
            ComposeView(this).apply {
                setContent {
                    PixelDeckTheme {
                        val s by app.store.settings.collectAsStateWithLifecycle(initialValue = settings)
                        val locked by produceState(keyguard.isKeyguardLocked) {
                            while (true) {
                                value = keyguard.isKeyguardLocked
                                delay(1_500)
                            }
                        }
                        CompositionLocalProvider(
                            LocalLock provides LockState(locked, s.lockedControls, s.lockedShowDetails),
                        ) { DashboardScreen() }
                    }
                }
            },
        )
    }

    override fun onDetachedFromWindow() {
        if (composeStarted) owner.moveTo(Lifecycle.State.DESTROYED)
        super.onDetachedFromWindow()
    }

    private fun isChargingWirelessly(): Boolean {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0
    }
}

private class DreamOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performAttach()
        controller.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
