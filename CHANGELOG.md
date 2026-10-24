# Changelog

## 0.2.0

Windows agent
- Single `pixeldeck-agent.exe`: double-click to run, no console window.
- Shows a pairing QR code window on first launch, hides it by itself after 90 seconds or as soon as a phone pairs.
- Tray icon with: show QR code, reset pairing, start with Windows, open settings folder, quit.
- Starts with Windows (per-user registry entry, no admin rights) and then stays hidden once a phone has paired.
- Second launch brings the QR window back instead of starting a second copy.
- Log file at `%APPDATA%\pixeldeck\agent.log`.

Android app
- QR code pairing (Google code scanner, no camera permission needed).
- Layout organizer: choose which panels the Home screen shows, move them between three columns, hide pages
  you do not use, set the number of stream deck columns.
- Timers (several at once), alarms (one-off or repeating on chosen days), stopwatch.
- Alarm and timer ringing screen that shows over the lock screen, with snooze and +1 min.
- Choose alarm, timer and notification sounds; optional dashboard chime for new notifications.
- New macro actions `start_timer` and `stop_ring`; new triggers `timer_done` and `alarm_ring`.
- Timers and alarms survive reboots and app updates.

## 0.1.0

- First version: clock, calendar, notifications, device hub, macros, stream deck, screen saver mode, PC agent.
