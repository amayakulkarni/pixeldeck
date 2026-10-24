//! PixelDeck agent: runs in the background on your PC and carries out the hotkeys, media keys,
//! launches and scripts that the PixelDeck phone app sends.
//!
//! Double-click the exe: a QR code window opens, scan it with the phone app, and the window hides
//! itself. The agent keeps running in the tray and starts with Windows.

#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]
#![cfg_attr(not(windows), allow(dead_code))]

mod app;
mod autostart;
mod config;
mod icon;
mod input;
mod keys;
mod logger;
mod pairing;
mod render;
mod server;
mod single;

#[cfg(windows)]
mod gui;
#[cfg(not(windows))]
mod headless;

use std::sync::{Arc, RwLock};

fn main() {
    logger::init();
    let args: Vec<String> = std::env::args().skip(1).collect();
    let background = args.iter().any(|a| a == "--background");
    let reset = args.iter().any(|a| a == "--reset-pairing");

    // A second launch just asks the running agent to show its QR code again.
    let control = match single::acquire() {
        single::Instance::Second => return,
        single::Instance::First(listener) => listener,
    };

    let (mut cfg, path) = match config::load_or_create() {
        Ok(x) => x,
        Err(e) => {
            logln!("cannot read or create config: {e}");
            return;
        }
    };
    if reset {
        cfg.token = config::new_token();
        cfg.paired_once = false;
        if let Err(e) = config::save(&cfg, &path) {
            logln!("could not save config: {e}");
        }
        logln!("pairing reset from command line");
    }
    logln!(
        "PixelDeck agent {} starting (config {})",
        env!("CARGO_PKG_VERSION"),
        path.display()
    );

    // Keep the Run entry pointing at the current exe location (also repairs it after the exe is moved).
    if let Err(e) = autostart::set(cfg.autostart) {
        logln!("autostart update failed: {e}");
    }

    // Show the QR code unless this is a quiet start with Windows and a phone has already paired.
    let show_qr = !background || !cfg.paired_once;
    let shared: app::Shared = Arc::new(RwLock::new(cfg));

    let rt = match tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()
    {
        Ok(rt) => rt,
        Err(e) => {
            logln!("cannot start async runtime: {e}");
            return;
        }
    };

    #[cfg(windows)]
    gui::run(rt, shared, control, show_qr);

    #[cfg(not(windows))]
    {
        let _ = show_qr;
        if let Ok(c) = shared.read() {
            headless::show_pairing(&c);
        }
        let notify: app::Notify = Arc::new(|_| {});
        rt.block_on(async {
            tokio::spawn(single::serve(control, notify.clone()));
            tokio::select! {
                _ = server::run(shared.clone(), notify) => {}
                _ = tokio::signal::ctrl_c() => {}
            }
        });
    }
}
