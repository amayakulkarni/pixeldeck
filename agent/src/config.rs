use rand::RngCore;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

fn default_true() -> bool {
    true
}

fn default_qr_seconds() -> u64 {
    90
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Config {
    /// TCP port the WebSocket server listens on.
    pub port: u16,
    /// Shared pairing secret. The phone sends it in its first "hello" message.
    pub token: String,
    /// Name shown on the phone and advertised over mDNS.
    pub device_name: String,
    /// Allow the phone to launch arbitrary programs / URLs.
    pub allow_launch: bool,
    /// Start the agent automatically when you sign in to Windows.
    #[serde(default = "default_true")]
    pub autostart: bool,
    /// How long the pairing QR window stays open before it hides itself.
    #[serde(default = "default_qr_seconds")]
    pub qr_seconds: u64,
    /// Set after the first successful phone connection. Until then the QR window also opens at sign-in.
    #[serde(default)]
    pub paired_once: bool,
    /// Named scripts the phone may run: name -> shell command line.
    #[serde(default)]
    pub scripts: BTreeMap<String, String>,
}

impl Default for Config {
    fn default() -> Self {
        let mut scripts = BTreeMap::new();
        scripts.insert(
            "example".to_string(),
            "echo hello from PixelDeck".to_string(),
        );
        Config {
            port: 47800,
            token: new_token(),
            device_name: hostname(),
            allow_launch: true,
            autostart: true,
            qr_seconds: default_qr_seconds(),
            paired_once: false,
            scripts,
        }
    }
}

pub fn new_token() -> String {
    let mut bytes = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut bytes);
    hex::encode(bytes)
}

pub fn hostname() -> String {
    std::env::var("COMPUTERNAME")
        .or_else(|_| std::env::var("HOSTNAME"))
        .unwrap_or_else(|_| "pc".to_string())
}

pub fn config_dir() -> PathBuf {
    let base = std::env::var_os("APPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")))
        .unwrap_or_else(|| PathBuf::from("."));
    base.join("pixeldeck")
}

pub fn config_path() -> PathBuf {
    config_dir().join("config.toml")
}

pub fn save(cfg: &Config, path: &Path) -> std::io::Result<()> {
    if let Some(dir) = path.parent() {
        std::fs::create_dir_all(dir)?;
    }
    let text = toml::to_string_pretty(cfg)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?;
    std::fs::write(path, text)
}

/// Load the config, creating it with a fresh random token on first run.
pub fn load_or_create() -> std::io::Result<(Config, PathBuf)> {
    let path = config_path();
    if let Ok(text) = std::fs::read_to_string(&path) {
        match toml::from_str::<Config>(&text) {
            Ok(cfg) => return Ok((cfg, path)),
            Err(e) => crate::logln!(
                "config parse error ({e}); delete {} to regenerate",
                path.display()
            ),
        }
    }
    let cfg = Config::default();
    save(&cfg, &path)?;
    Ok((cfg, path))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrips() {
        let cfg = Config::default();
        let text = toml::to_string_pretty(&cfg).unwrap();
        let back: Config = toml::from_str(&text).unwrap();
        assert_eq!(cfg, back);
    }

    #[test]
    fn old_config_without_new_fields_still_loads() {
        let old = r#"
port = 47800
token = "abc123"
device_name = "DESKTOP"
allow_launch = true

[scripts]
backup = "echo hi"
"#;
        let cfg: Config = toml::from_str(old).unwrap();
        assert_eq!(cfg.token, "abc123");
        assert!(cfg.autostart);
        assert!(!cfg.paired_once);
        assert_eq!(cfg.qr_seconds, 90);
    }

    #[test]
    fn tokens_are_32_hex_chars_and_unique() {
        let (a, b) = (new_token(), new_token());
        assert_eq!(a.len(), 32);
        assert!(a.chars().all(|c| c.is_ascii_hexdigit()));
        assert_ne!(a, b);
    }
}
