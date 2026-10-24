//! Non-Windows fallback (used for development on Linux/macOS): no window, prints the pairing info.

use crate::{config, pairing};

pub fn show_pairing(cfg: &config::Config) {
    let ip = pairing::host_ip();
    let text = pairing::pairing_string(&ip, cfg.port, &cfg.token, &cfg.device_name);
    println!("PixelDeck agent {}", env!("CARGO_PKG_VERSION"));
    println!("Pair your phone with this string (or build it into a QR code): ");
    println!("    {text}");
}
