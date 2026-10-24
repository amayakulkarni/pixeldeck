package dev.pixeldeck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.pixeldeck.notif.DashNotificationListener
import dev.pixeldeck.service.DashboardService
import dev.pixeldeck.ui.DashboardScreen
import dev.pixeldeck.ui.LocalRequestPermissions
import dev.pixeldeck.ui.PixelDeckTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Calendar data becomes available the moment READ_CALENDAR is granted.
        val app = PixelDeckApp.instance
        app.scope.launch { app.calendar.refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        DashboardService.start(this)
        DashNotificationListener.requestRebind(this)

        setContent {
            PixelDeckTheme {
                CompositionLocalProvider(LocalRequestPermissions provides { perms -> permissionLauncher.launch(perms) }) {
                    DashboardScreen()
                }
            }
        }
    }
}
