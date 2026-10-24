//! Builds the pairing string shown as a QR code and typed/pasted on the phone.
//!
//! Format: `pixeldeck://<ip>:<port>/<token>?n=<url-encoded PC name>`

use crate::render::Qr;
use qrcode::{Color, QrCode};

pub fn host_ip() -> String {
    local_ip_address::local_ip()
        .map(|ip| ip.to_string())
        .unwrap_or_else(|_| "127.0.0.1".to_string())
}

fn encode_component(text: &str) -> String {
    let mut out = String::new();
    for b in text.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

pub fn pairing_string(ip: &str, port: u16, token: &str, name: &str) -> String {
    format!(
        "pixeldeck://{ip}:{port}/{token}?n={}",
        encode_component(name)
    )
}

pub fn qr(data: &str) -> Option<Qr> {
    let code = QrCode::new(data.as_bytes()).ok()?;
    let size = code.width();
    let modules = code
        .to_colors()
        .into_iter()
        .map(|c| c == Color::Dark)
        .collect();
    Some(Qr { size, modules })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builds_pairing_string() {
        assert_eq!(
            pairing_string("192.168.1.5", 47800, "ab12", "My PC"),
            "pixeldeck://192.168.1.5:47800/ab12?n=My%20PC"
        );
    }

    #[test]
    fn qr_is_square_and_nonempty() {
        let q = qr(&pairing_string(
            "192.168.1.5",
            47800,
            "0123456789abcdef0123456789abcdef",
            "DESKTOP-ABC123",
        ))
        .unwrap();
        assert!(q.size >= 21);
        assert_eq!(q.modules.len(), q.size * q.size);
        assert!(q.modules.iter().any(|&m| m));
    }
}
