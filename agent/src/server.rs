//! WebSocket server that the phone talks to (protocol v1, see docs/PROTOCOL.md).

use crate::app::{Notify, Shared, UiEvent};
use crate::config::{self, Config};
use crate::input;
use crate::logln;
use futures_util::{SinkExt, StreamExt};
use mdns_sd::{ServiceDaemon, ServiceInfo};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::process::Command;
use std::time::Duration;
use tokio::net::{TcpListener, TcpStream};
use tokio::time::{interval, timeout};
use tokio_tungstenite::tungstenite::Message;

const PROTOCOL_VERSION: u32 = 1;
const SERVICE_TYPE: &str = "_pixeldeck._tcp.local.";

#[derive(Debug, Deserialize)]
struct Envelope {
    #[allow(dead_code)]
    v: u32,
    id: String,
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    payload: Value,
}

#[derive(Debug, Serialize)]
struct Reply {
    v: u32,
    id: String,
    #[serde(rename = "type")]
    kind: String,
    payload: Value,
}

fn reply(id: &str, kind: &str, payload: Value) -> Message {
    let r = Reply {
        v: PROTOCOL_VERSION,
        id: id.to_string(),
        kind: kind.to_string(),
        payload,
    };
    Message::Text(serde_json::to_string(&r).expect("serialize reply"))
}

/// Constant-time token comparison.
fn token_ok(given: &str, expected: &str) -> bool {
    let (a, b) = (given.as_bytes(), expected.as_bytes());
    if a.len() != b.len() {
        return false;
    }
    a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

fn launch(target: &str, args: &[String]) -> Result<(), String> {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        // `start` handles exes, documents and URLs. The empty "" is the window title argument.
        let mut cmd = Command::new("cmd");
        cmd.args(["/C", "start", "", target])
            .args(args)
            .creation_flags(CREATE_NO_WINDOW);
        cmd.spawn().map(|_| ()).map_err(|e| e.to_string())
    }
    #[cfg(not(windows))]
    {
        Command::new(target)
            .args(args)
            .spawn()
            .map(|_| ())
            .map_err(|e| e.to_string())
    }
}

fn run_script(command_line: &str) -> Result<String, String> {
    #[cfg(windows)]
    let out = {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        Command::new("cmd")
            .args(["/C", command_line])
            .creation_flags(CREATE_NO_WINDOW)
            .output()
    };
    #[cfg(not(windows))]
    let out = Command::new("sh").args(["-c", command_line]).output();
    let out = out.map_err(|e| e.to_string())?;
    let mut text = String::from_utf8_lossy(&out.stdout).to_string();
    text.truncate(2000);
    if out.status.success() {
        Ok(text)
    } else {
        Err(format!(
            "exit {:?}: {}",
            out.status.code(),
            String::from_utf8_lossy(&out.stderr)
        ))
    }
}

/// Execute one action. Returns Ok(payload) for an ack, Err(message) for an error reply.
fn execute(env: &Envelope, cfg: &Config) -> Result<Value, String> {
    let p = &env.payload;
    let s = |k: &str| p.get(k).and_then(Value::as_str).unwrap_or("").to_string();
    match env.kind.as_str() {
        "ping" => Ok(json!({})),
        "hotkey" => {
            input::hotkey(&s("keys"))?;
            Ok(json!({}))
        }
        "type_text" => {
            input::type_text(&s("text"));
            Ok(json!({}))
        }
        "media" => {
            let key = match s("action").as_str() {
                "play_pause" => "playpause",
                "next" => "next",
                "prev" => "prev",
                "stop" => "stop",
                "vol_up" => "volup",
                "vol_down" => "voldown",
                "mute" => "mute",
                other => return Err(format!("unknown media action '{other}'")),
            };
            input::hotkey(key)?;
            Ok(json!({}))
        }
        "launch" => {
            if !cfg.allow_launch {
                return Err("launch is disabled in config.toml".into());
            }
            let args: Vec<String> = p
                .get("args")
                .and_then(Value::as_array)
                .map(|a| {
                    a.iter()
                        .filter_map(|v| v.as_str().map(String::from))
                        .collect()
                })
                .unwrap_or_default();
            launch(&s("target"), &args)?;
            Ok(json!({}))
        }
        "script" => {
            let name = s("name");
            let cmd = cfg
                .scripts
                .get(&name)
                .ok_or_else(|| format!("no script named '{name}' in config.toml"))?;
            let out = run_script(cmd)?;
            Ok(json!({ "output": out }))
        }
        other => Err(format!("unknown message type '{other}'")),
    }
}

fn state_message(cfg: &Config) -> Message {
    reply(
        "state",
        "state",
        json!({
            "name": cfg.device_name,
            "os": std::env::consts::OS,
            "scripts": cfg.scripts.keys().collect::<Vec<_>>(),
            "allowLaunch": cfg.allow_launch,
        }),
    )
}

fn snapshot(shared: &Shared) -> Config {
    shared.read().map(|c| c.clone()).unwrap_or_default()
}

async fn handle(stream: TcpStream, shared: Shared, notify: Notify) {
    let peer = stream
        .peer_addr()
        .map(|a| a.to_string())
        .unwrap_or_default();
    let ws = match tokio_tungstenite::accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => {
            logln!("[{peer}] handshake failed: {e}");
            return;
        }
    };
    let (mut tx, mut rx) = ws.split();

    // The first message must be a valid hello within 5 seconds.
    let first = match timeout(Duration::from_secs(5), rx.next()).await {
        Ok(Some(Ok(Message::Text(t)))) => t,
        _ => {
            logln!("[{peer}] no hello, closing");
            return;
        }
    };
    let hello: Envelope = match serde_json::from_str(&first) {
        Ok(h) => h,
        Err(_) => return,
    };
    let cfg = snapshot(&shared);
    let given = hello
        .payload
        .get("token")
        .and_then(Value::as_str)
        .unwrap_or("");
    if hello.kind != "hello" || !token_ok(given, &cfg.token) {
        let _ = tx
            .send(reply(&hello.id, "error", json!({ "message": "bad token" })))
            .await;
        let _ = tx.close().await;
        logln!("[{peer}] rejected: bad token");
        return;
    }
    let session_token = cfg.token.clone();
    let client = hello
        .payload
        .get("client")
        .and_then(Value::as_str)
        .unwrap_or("phone")
        .to_string();
    logln!("[{peer}] paired session started ({client})");
    let _ = tx
        .send(reply(&hello.id, "ack", json!({ "name": cfg.device_name })))
        .await;
    let _ = tx.send(state_message(&cfg)).await;

    // Remember that a phone has paired at least once so the QR window stops opening at every sign-in.
    let first_pairing = shared.read().map(|c| !c.paired_once).unwrap_or(false);
    if first_pairing {
        if let Ok(mut c) = shared.write() {
            c.paired_once = true;
            if let Err(e) = config::save(&c, &config::config_path()) {
                logln!("could not save config: {e}");
            }
        }
    }
    notify(UiEvent::Paired);

    let mut ticker = interval(Duration::from_secs(10));
    loop {
        tokio::select! {
            msg = rx.next() => {
                let text = match msg {
                    Some(Ok(Message::Text(t))) => t,
                    Some(Ok(Message::Close(_))) | None | Some(Err(_)) => break,
                    Some(Ok(_)) => continue,
                };
                let env: Envelope = match serde_json::from_str(&text) {
                    Ok(e) => e,
                    Err(e) => {
                        let _ = tx.send(reply("?", "error", json!({ "message": format!("bad json: {e}") }))).await;
                        continue;
                    }
                };
                let cfg2 = snapshot(&shared);
                // Input injection and scripts can block briefly; keep them off the reactor.
                let result = tokio::task::spawn_blocking(move || {
                    let r = execute(&env, &cfg2);
                    (env.id, r)
                }).await;
                let out = match result {
                    Ok((id, Ok(payload))) => reply(&id, "ack", payload),
                    Ok((id, Err(m))) => reply(&id, "error", json!({ "message": m })),
                    Err(e) => reply("?", "error", json!({ "message": e.to_string() })),
                };
                if tx.send(out).await.is_err() { break; }
            }
            _ = ticker.tick() => {
                let current = snapshot(&shared);
                if current.token != session_token {
                    logln!("[{peer}] token was reset, closing session");
                    let _ = tx.send(reply("state", "error", json!({ "message": "pairing was reset on the PC" }))).await;
                    let _ = tx.close().await;
                    break;
                }
                if tx.send(state_message(&current)).await.is_err() { break; }
            }
        }
    }
    logln!("[{peer}] session ended");
}

fn advertise(cfg: &Config) -> Option<ServiceDaemon> {
    let daemon = ServiceDaemon::new().ok()?;
    let ip = local_ip_address::local_ip().ok()?;
    let host = format!("{}.local.", config::hostname());
    let mut props = std::collections::HashMap::new();
    props.insert("v".to_string(), PROTOCOL_VERSION.to_string());
    let info = ServiceInfo::new(
        SERVICE_TYPE,
        &cfg.device_name,
        &host,
        ip.to_string(),
        cfg.port,
        props,
    )
    .ok()?;
    daemon.register(info).ok()?;
    Some(daemon)
}

/// Run the server until the task is dropped. Retries binding for a while at boot because the
/// network stack may not be ready yet when the agent starts with Windows.
pub async fn run(shared: Shared, notify: Notify) {
    let cfg = snapshot(&shared);
    let _mdns = advertise(&cfg);
    if _mdns.is_none() {
        logln!("warning: mDNS advertising failed; enter the host manually on the phone");
    }
    let mut attempts = 0;
    let listener = loop {
        match TcpListener::bind(("0.0.0.0", cfg.port)).await {
            Ok(l) => break l,
            Err(e) => {
                attempts += 1;
                logln!(
                    "cannot listen on port {} (attempt {attempts}): {e}",
                    cfg.port
                );
                if attempts >= 30 {
                    return;
                }
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        }
    };
    logln!("listening on 0.0.0.0:{}", cfg.port);
    loop {
        if let Ok((stream, _)) = listener.accept().await {
            tokio::spawn(handle(stream, shared.clone(), notify.clone()));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn env(kind: &str, payload: Value) -> Envelope {
        Envelope {
            v: 1,
            id: "1".into(),
            kind: kind.into(),
            payload,
        }
    }

    #[test]
    fn token_compare() {
        assert!(token_ok("abc", "abc"));
        assert!(!token_ok("abc", "abd"));
        assert!(!token_ok("abc", "abcd"));
        assert!(!token_ok("", "abc"));
    }

    #[test]
    fn ping_and_unknown() {
        let cfg = Config::default();
        assert!(execute(&env("ping", json!({})), &cfg).is_ok());
        assert!(execute(&env("nope", json!({})), &cfg).is_err());
    }

    #[test]
    fn launch_can_be_disabled() {
        let cfg = Config {
            allow_launch: false,
            ..Config::default()
        };
        let err = execute(&env("launch", json!({"target": "notepad"})), &cfg).unwrap_err();
        assert!(err.contains("disabled"));
    }

    #[test]
    fn unknown_script_and_media_are_errors() {
        let cfg = Config::default();
        assert!(execute(&env("script", json!({"name": "missing"})), &cfg).is_err());
        assert!(execute(&env("media", json!({"action": "explode"})), &cfg).is_err());
    }
}
