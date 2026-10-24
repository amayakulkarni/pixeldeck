//! Tiny file logger. The release build has no console window, so everything goes to
//! %APPDATA%\pixeldeck\agent.log (kept under 512 KB).

use std::fs::{File, OpenOptions};
use std::io::Write;
use std::sync::{Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

static LOG: OnceLock<Mutex<File>> = OnceLock::new();

pub fn init() {
    let path = crate::config::config_dir().join("agent.log");
    if let Some(dir) = path.parent() {
        let _ = std::fs::create_dir_all(dir);
    }
    if std::fs::metadata(&path)
        .map(|m| m.len() > 512 * 1024)
        .unwrap_or(false)
    {
        let _ = std::fs::remove_file(&path);
    }
    if let Ok(file) = OpenOptions::new().create(true).append(true).open(&path) {
        let _ = LOG.set(Mutex::new(file));
    }
    std::panic::set_hook(Box::new(|info| write(&format!("PANIC: {info}"))));
}

pub fn write(msg: &str) {
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let line = format!("[{secs}] {msg}\n");
    if let Some(file) = LOG.get() {
        if let Ok(mut f) = file.lock() {
            let _ = f.write_all(line.as_bytes());
        }
    }
    #[cfg(not(windows))]
    eprint!("{line}");
}

#[macro_export]
macro_rules! logln {
    ($($arg:tt)*) => { $crate::logger::write(&format!($($arg)*)) };
}
