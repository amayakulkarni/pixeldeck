package dev.pixeldeck.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import dev.pixeldeck.PixelDeckApp
import dev.pixeldeck.alarm.RingState
import dev.pixeldeck.data.Panels
import dev.pixeldeck.data.Settings
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class Page(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Dashboard),
    Timers("Timers", Icons.Default.Timer),
    Deck("Deck", Icons.Default.Apps),
    Devices("Devices", Icons.Default.DevicesOther),
    Macros("Macros", Icons.Default.Bolt),
    Layout("Layout", Icons.Default.ViewQuilt),
    Settings("Settings", Icons.Default.Settings),
}

@Composable
fun DashboardScreen() {
    val app = PixelDeckApp.instance
    val settings by app.store.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var requestedPage by rememberSaveable { mutableStateOf(Page.Home) }
    val lock = LocalLock.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ringing by RingState.current.collectAsStateWithLifecycle()
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    var bannerNote by remember { mutableStateOf<String?>(null) }

    // Pages the user hid in the layout organizer disappear from the rail; fall back to Home if the current one is hidden.
    val visiblePages = Page.entries.filter { it.name !in settings.hiddenPages || it !in HIDEABLE_PAGES }
    val page = if (requestedPage in visiblePages) requestedPage else Page.Home

    // ---- idle / night mode ----
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val idle = settings.nightMode && now - lastTouch > settings.dimAfterMinutes * 60_000L

    // ---- keep screen on ----
    val activity = LocalContext.current as? Activity
    DisposableEffect(settings.keepAwake) {
        val window = activity?.window
        if (settings.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ---- slow pixel shift against OLED burn-in ----
    var target by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(settings.pixelShift) {
        while (true) {
            target = if (settings.pixelShift) IntOffset(Random.nextInt(-8, 9), Random.nextInt(-8, 9)) else IntOffset.Zero
            delay(60_000)
        }
    }
    val shift by animateIntOffsetAsState(target, tween(5_000), label = "pixelShift")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxSize().offset { shift }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // While locked (screen saver over the lock screen) only Home is reachable, so Settings and tokens stay hidden.
            if (!lock.locked) Column(
                Modifier.width(72.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visiblePages.forEach { p ->
                    NavigationRailItem(
                        selected = page == p,
                        onClick = { requestedPage = p },
                        icon = { Icon(p.icon, contentDescription = p.label) },
                        label = { Text(p.label) },
                        alwaysShowLabel = false,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            indicatorColor = Magenta,
                            unselectedIconColor = Muted,
                            selectedTextColor = Magenta,
                        ),
                    )
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!lock.locked && !settings.pcConfigured && !bannerDismissed) {
                    PairBanner(
                        note = bannerNote,
                        onScan = {
                            scanPairing(
                                ctx,
                                onResult = { p -> scope.launch { app.applyPairing(p) }; bannerNote = null },
                                onMessage = { bannerNote = it },
                            )
                        },
                        onSettings = { requestedPage = Page.Settings },
                        onDismiss = { bannerDismissed = true },
                    )
                }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (if (lock.locked) Page.Home else page) {
                    Page.Home -> HomePage(settings, onOpenPage = { requestedPage = it })
                    Page.Timers -> TimersPage(settings, Modifier.fillMaxSize())
                    Page.Layout -> LayoutPanel(settings, Modifier.fillMaxSize())
                    Page.Deck -> DeckPanel(columns = 5, modifier = Modifier.fillMaxSize(), showLog = true)
                    Page.Devices -> IotPanel(settings, Modifier.fillMaxSize())
                    Page.Macros -> MacrosPanel(Modifier.fillMaxSize())
                    Page.Settings -> SettingsPanel(settings, Modifier.fillMaxSize())
                }
            }
            }
        }

        if (idle) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        lastTouch = System.currentTimeMillis()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.offset { IntOffset(shift.x * 6, shift.y * 6) }) { NightClock(settings.use24h) }
            }
        }

        // An alarm or timer is ringing: show it over everything, including night mode.
        ringing?.let { RingCard(it) }
    }
}

@Composable
private fun PairBanner(note: String?, onScan: () -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    PanelCard("Pair your PC", Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                note ?: "Run the PixelDeck agent on your PC, then scan the QR code it shows.",
                Modifier.weight(1f), color = if (note == null) Muted else Bad,
            )
            PrimaryButton("Scan QR code", onScan)
            TextButton(onClick = onSettings) { Text("Enter manually") }
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    }
}

@Composable
private fun HomePage(settings: Settings, onOpenPage: (Page) -> Unit) {
    val columns = Panels.sanitize(settings.homeLayout).filter { it.isNotEmpty() }
    if (columns.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("All panels are hidden.", color = Muted)
                TextButton(onClick = { onOpenPage(Page.Layout) }) { Text("Open layout organizer") }
            }
        }
        return
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        columns.forEach { keys ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // If every panel in the column is compact, let the last one take the leftover height.
                val allCompact = keys.all { it in Panels.COMPACT }
                keys.forEachIndexed { i, key ->
                    val stretch = key !in Panels.COMPACT || (allCompact && i == keys.lastIndex)
                    val mod = if (stretch) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth()
                    when (key) {
                        "clock" -> ClockPanel(settings.use24h, mod)
                        "timers" -> TimersCompact(settings, onOpen = { onOpenPage(Page.Timers) }, modifier = mod)
                        "calendar" -> CalendarPanel(mod)
                        "notifications" -> NotificationsPanel(mod)
                        "deck" -> DeckPanel(columns = settings.deckColumns, modifier = mod, showLog = true)
                    }
                }
            }
        }
    }
}
