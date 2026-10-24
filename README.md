# PixelDeck

Turn a spare Android phone (built and tested for a Pixel 8) into a dock dashboard, and control your Windows PC from it.

- **Dashboard**: clock, calendar, notifications, timers and alarms, stream deck, and a hub for Home Assistant, Wi-Fi and Bluetooth devices.
- **Stream deck and macros**: tap a tile to press hotkeys, type text, control media, launch programs, run scripts, call Home Assistant or any web URL.
- **Windows agent**: one small `.exe` that runs quietly in the tray, starts with Windows, and shows a QR code once so you can pair.
- **Make it yours**: pick which panels appear, where they sit, and which pages exist.
- **Charging dock mode**: runs as a screen saver on a wireless charger, even over the lock screen.

```
pixeldeck/
  android/   Android app (Kotlin, Jetpack Compose)
  agent/     Windows agent (Rust)
  docs/      Protocol description
```

## Contents

1. [Install](#1-install)
2. [Pair the phone with your PC](#2-pair-the-phone-with-your-pc)
3. [Find your way around the app](#3-find-your-way-around-the-app)
4. [Organize your layout](#4-organize-your-layout)
5. [Timers, alarms and sounds](#5-timers-alarms-and-sounds)
6. [Macros and the stream deck](#6-macros-and-the-stream-deck)
7. [Use it as a charging dock](#7-use-it-as-a-charging-dock)
8. [The Windows agent](#8-the-windows-agent)
9. [Permissions](#9-permissions)
10. [Troubleshooting](#10-troubleshooting)
11. [Privacy and security](#11-privacy-and-security)
12. [Building from source](#12-building-from-source)

## 1. Install

Go to the **Releases** page of this repository and download:

| File | Where it goes |
|---|---|
| `PixelDeck-<version>.apk` | the Android phone |
| `pixeldeck-agent.exe` | the Windows PC |

**Phone**: open the APK on the phone (or copy it over). Android asks permission to install from that source; allow it once.
You need Android 12 or newer.

**PC**: put `pixeldeck-agent.exe` in a folder you will keep it in (for example `C:\Tools\PixelDeck`) and double-click it.
Windows SmartScreen may say the app is unrecognized because it is not code-signed: choose **More info > Run anyway**.
Allow it through the Windows Firewall on **private networks** when asked.

Do not move the exe afterwards. If you do, just run it once from its new place and it fixes its start-up entry.

## 2. Pair the phone with your PC

1. Start `pixeldeck-agent.exe`. A window with a **QR code** appears.
2. On the phone open PixelDeck, tap **Scan QR code** (the banner on the Home page, or Settings).
3. Point the camera at the QR code. The phone saves the PC and connects. The QR window closes by itself.

The phone and the PC must be on the same Wi-Fi network. If you missed the window, double-click the tray icon,
or start the exe again, and it comes back.

Scanning does not need camera permission; Google's scanner (part of Google Play services) handles it. If the scanner is not
available, Settings also has fields for the host, port and token, and a box to paste the `pixeldeck://...` text.

## 3. Find your way around the app

The bar on the left switches pages:

| Page | What it does |
|---|---|
| **Home** | Your chosen panels: clock, timers, calendar, notifications, stream deck. |
| **Timers** | Timers, alarms and a stopwatch. |
| **Deck** | A big stream deck. Tap a tile to run it, **Edit** to add, change or remove tiles. |
| **Devices** | Home Assistant devices, devices found on Wi-Fi, Bluetooth scan. |
| **Macros** | Create and edit macros and their triggers. |
| **Layout** | Choose and arrange the panels on Home, hide pages. |
| **Settings** | PC link, Home Assistant, display, sounds, permissions, backup. |

When the screen is idle for a few minutes (default 5) it dims to a small clock on pure black to protect the OLED. Touch the screen to wake it.

## 4. Organize your layout

Open the **Layout** page. Nothing here needs a Save button; changes apply right away.

- The Home page has **three columns**. Each panel (Clock, Timers and alarms, Calendar, Notifications, Stream deck) has arrows to move it
  left, right, up or down, and a cross to hide it.
- Hidden panels are listed below the columns with a **Show** button.
- Leave a column empty and the other columns share the space. Do not want a stream deck or a calendar? Hide it.
- **Pages in the side bar**: switch off Timers, Deck, Devices or Macros if you never use them. Home, Layout and Settings always stay.
- **Stream deck columns on Home**: how many tiles per row on the Home page.
- **Reset to default layout** puts everything back.

## 5. Timers, alarms and sounds

**Timers** (Timers page, or the quick buttons on Home): tap 1m, 5m, 10m or 25m, or type a duration such as `10m`, `1h30m`, `90s`, `05:00`
(a plain number means minutes). Add a label if you like. Run as many at once as you want.

**Alarms**: tap **New alarm**, set the time, optionally a label, and tap the day letters (M T W T F S S) to repeat. No days selected
means the alarm rings once and then switches itself off. Use the switch to turn an alarm on or off without deleting it, tap an alarm to edit it.

**When it rings**: sound and vibration start, and a full-screen card appears, also over the lock screen. **Dismiss** stops it.
**Snooze** (alarms) rings again after the snooze time, **+1 min** (timers) adds a minute. It stops by itself after a minute by default.

**Stopwatch**: Start/Stop, Lap, Reset. It keeps running while you look at other pages.

**Sounds** (Settings > Sounds and timers):
- Choose the alarm sound and the timer sound from the phone's ringtone list, or Silent (vibration only). **Test** plays it.
- **Dashboard chime**: plays a sound of your choice when a new notification arrives on the dashboard. The phone still plays its own sound as well.
- Snooze length and how long an alarm rings can be changed.

Alarms and timers are kept across restarts and reboots. If the phone is **off** when a timer should finish, that timer is dropped quietly.
Alarms are not affected: they are scheduled again at boot.

## 6. Macros and the stream deck

A **macro** is an ordered list of steps. A **deck tile** runs a macro. A macro can also run by itself from a **trigger**.

Steps:

| Step | Argument | Example |
|---|---|---|
| PC hotkey | key combination | `ctrl+shift+esc`, `win+d`, `alt+f4`, `f13` |
| Type text on PC | text | `hello@example.com` |
| PC media key | `play_pause next prev stop vol_up vol_down mute` | `play_pause` |
| Launch on PC | program, file or URL, optional arguments | `https://example.com` |
| PC script | a script name from the agent's `config.toml` | `backup` |
| HTTP request | URL, GET or POST | `http://192.168.1.20/relay/on` |
| Home Assistant service | `domain.service` and entity | `light.toggle` / `light.desk` |
| Open Android app | package name | `com.spotify.music` |
| Start timer | duration, label | `25m` / `Focus` |
| Silence ringing | none | stops a ringing alarm or timer |
| Wait | milliseconds | `500` |

Triggers: manual, notification from an app (or any app), charger connected, charger removed, a time every day, a timer that finishes, an alarm that rings.

To edit the deck: **Deck > Edit**. To make a new tile, create the macro first, then add a tile that points at it.

**Run your own scripts on the PC**: open `%APPDATA%\pixeldeck\config.toml` (tray icon > Open settings folder) and add lines under `[scripts]`:

```toml
[scripts]
backup = "robocopy D:\\Work E:\\Backup /MIR"
vpn_on = "C:\\Tools\\vpn.bat connect"
```

Then use a **PC script** step with the name `backup`. The phone can only run names listed here, never arbitrary commands.
Restart the agent (tray icon > Quit, then start it again) after editing the file.

## 7. Use it as a charging dock

PixelDeck registers as an Android **screen saver**, which Android shows above the lock screen while the phone charges.

1. On the phone: Settings > Display > Screen saver (or search for it) > choose **PixelDeck**.
2. Set **When to start** to **While charging**.
3. Put the phone on the wireless charger. After the normal screen timeout the dashboard appears. **Start now** on that settings page tests it.

By default the screen saver only runs on a **wireless** charger (change this in PixelDeck Settings). A locked phone shows only the Home page,
with notification text hidden, calendar titles shown as "Busy" and deck taps disabled. Two switches in Settings relax this.
An alarm or timer that rings still shows its Dismiss and Snooze buttons.

## 8. The Windows agent

- **Tray icon** (bottom-right, maybe under the ^ arrow). Right-click for: *Show pairing QR code*, *Reset pairing*, *Start with Windows*, *Open settings folder*, *Quit*. Double-click shows the QR code.
- **Start with Windows** is on by default. After the first successful pairing the agent starts hidden at sign-in. Switch it off in the tray menu.
- **Reset pairing** creates a new secret. Phones paired before are disconnected and must scan the new QR code. Do this if the QR code was ever seen by someone else.
- Starting the exe a second time just brings the QR code back; there is only ever one copy running.
- Command line: `pixeldeck-agent.exe --background` (start hidden), `--reset-pairing` (new secret, then show the QR code).
- Files are in `%APPDATA%\pixeldeck\`: `config.toml` (port, secret, scripts, `allow_launch`, `autostart`, `qr_seconds`) and `agent.log`.
- Keystrokes cannot go into windows running as Administrator unless the agent also runs as Administrator.
- To remove it: tray icon > untick *Start with Windows*, choose *Quit*, then delete the exe and the `%APPDATA%\pixeldeck` folder.

## 9. Permissions

| Permission | Why | Needed for |
|---|---|---|
| Notification access | read notifications for the feed and triggers | Notifications panel |
| Calendar | read the calendars already on the phone | Calendar panel |
| Post notifications | show the ringing alarm notification | Alarms and timers |
| Bluetooth scan | list nearby Bluetooth LE devices | Devices > Scan |
| Full-screen notifications | show the ringing card over the lock screen | Alarms and timers |

If Notification access is greyed out for the sideloaded app: Settings > Apps > PixelDeck > three-dot menu > **Allow restricted settings**, then try again.
Scheduling an alarm needs no extra permission.

## 10. Troubleshooting

| Problem | Try this |
|---|---|
| Phone says "Offline" or never connects | Same Wi-Fi? Router "client isolation" off? Windows Firewall allows the agent on private networks? Scan the QR code again. |
| QR scanner says unavailable | Update Google Play services, or paste the pairing text / type host, port and token in Settings. |
| "Rejected: wrong token" | The pairing was reset on the PC. Scan the new QR code. |
| Hotkeys do nothing in one program | That program runs as Administrator. Run the agent as Administrator too. |
| QR window never appears at start-up | By design after the first pairing. Use the tray icon, or start the exe again. |
| No tray icon | Check the ^ overflow area, or look at `%APPDATA%\pixeldeck\agent.log`. |
| Alarm did not ring | Check the phone is not in a mode that blocks alarms, the alarm switch is on, and Settings > Apps > PixelDeck > Battery is not "Restricted". |
| No sound from the dashboard chime | Turn on *Dashboard chime*, press **Test**, check the media/notification volume. |
| Screen saver does not start | It needs charging, a screen timeout, and PixelDeck selected under Screen saver. Check "only on wireless" if you use a cable. |
| Notifications empty | Grant Notification access (see Permissions). |

## 11. Privacy and security

- Everything stays on your network. There are no accounts, no cloud service and no analytics.
- The phone and the PC talk over a plain (unencrypted) WebSocket, protected by a secret token from the QR code. Use it on a network you trust and
  never forward the port to the internet. For use away from home, connect both devices through a VPN such as WireGuard.
- Anyone who can scan the QR code can control the PC. The window hides itself quickly; use *Reset pairing* if in doubt.
- Home Assistant tokens and the PC secret are stored in the app's private storage on the phone and are left out of the backup.
- The protocol is documented in [docs/PROTOCOL.md](docs/PROTOCOL.md).

## 12. Building from source

Releases are built automatically by GitHub Actions when a version tag such as `v0.2.0` is pushed (see `.github/workflows/build.yml`).
To build yourself you need [Rust](https://rustup.rs) for the agent and Android Studio (or JDK 17 and the Android SDK) for the app:

```
cd agent   &&  cargo build --release          # agent/target/release/pixeldeck-agent.exe  (build on Windows)
cd android &&  ./gradlew assembleRelease      # android/app/build/outputs/apk/release/app-release.apk
```

Unit tests: `cargo test` in `agent/`, `./gradlew testDebugUnitTest` in `android/`.

## License

MIT, see [LICENSE](LICENSE).
