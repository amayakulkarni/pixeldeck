# PixelDeck protocol, version 1

The phone connects to the PC agent over a WebSocket on TCP port 47800 (configurable in `config.toml`).
Messages are JSON text frames:

```json
{ "v": 1, "id": "abc123", "type": "hotkey", "payload": { "keys": "ctrl+shift+esc" } }
```

`id` is chosen by the sender; the reply carries the same `id`.

## Handshake

The first frame from the phone must be, within 5 seconds:

```json
{ "v": 1, "id": "1", "type": "hello", "payload": { "token": "<pairing token>", "client": "phone" } }
```

The agent answers `ack` (with its `name`) followed by a `state` frame, or `error` with `"bad token"` and closes
the socket. The token is compared in constant time.

## Phone to PC

| type        | payload                                   | effect                                             |
|-------------|-------------------------------------------|----------------------------------------------------|
| `ping`      | none                                      | `ack`                                              |
| `hotkey`    | `keys`: `"ctrl+shift+esc"`, `"win+d"` ... | presses the combination                            |
| `type_text` | `text`                                    | types the text as Unicode input                    |
| `media`     | `action`: `play_pause next prev stop vol_up vol_down mute` | media key           |
| `launch`    | `target`, optional `args` (array)         | opens a program, file or URL (needs `allow_launch`) |
| `script`    | `name`                                    | runs the named command from `[scripts]` in config  |

## PC to phone

| type    | payload                                                        |
|---------|----------------------------------------------------------------|
| `ack`   | optional result, e.g. `output` for scripts                     |
| `error` | `message`                                                      |
| `state` | `name`, `os`, `scripts` (names), `allowLaunch`; sent every 10 s |

If the token is reset on the PC (tray menu > Reset pairing) open sessions are closed within 10 seconds.

## Pairing string and QR code

```
pixeldeck://<ip>:<port>/<token>?n=<url-encoded PC name>
```

The `?n=` part is optional. The PC also advertises `_pixeldeck._tcp.local.` over mDNS so the phone can list it.

## Security notes

- Traffic is not encrypted. Use it only on a network you trust, never expose the port to the internet.
- The token is the only credential. Anyone who can read the QR code can control the PC, so do not
  leave the QR window up on a shared screen. It hides itself after a short time.
- Scripts are limited to the names in `config.toml`; the phone cannot send arbitrary shell commands.
