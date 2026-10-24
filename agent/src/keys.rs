//! Platform-independent key name parsing. "ctrl+shift+esc" -> [VK_CONTROL, VK_SHIFT, VK_ESCAPE].

pub fn vk_for(name: &str) -> Option<u16> {
    let n = name.trim().to_ascii_lowercase();
    let vk = match n.as_str() {
        "ctrl" | "control" => 0x11,
        "shift" => 0x10,
        "alt" | "menu" => 0x12,
        "win" | "meta" | "super" | "cmd" => 0x5B,
        "enter" | "return" => 0x0D,
        "esc" | "escape" => 0x1B,
        "tab" => 0x09,
        "space" => 0x20,
        "backspace" => 0x08,
        "delete" | "del" => 0x2E,
        "insert" | "ins" => 0x2D,
        "home" => 0x24,
        "end" => 0x23,
        "pageup" | "pgup" => 0x21,
        "pagedown" | "pgdn" => 0x22,
        "left" => 0x25,
        "up" => 0x26,
        "right" => 0x27,
        "down" => 0x28,
        "printscreen" | "prtsc" => 0x2C,
        "capslock" => 0x14,
        "playpause" => 0xB3,
        "next" | "nexttrack" => 0xB0,
        "prev" | "prevtrack" => 0xB1,
        "stop" => 0xB2,
        "volup" | "volumeup" => 0xAF,
        "voldown" | "volumedown" => 0xAE,
        "mute" | "volumemute" => 0xAD,
        "-" | "minus" => 0xBD,
        "=" | "plus" | "equals" => 0xBB,
        "," | "comma" => 0xBC,
        "." | "period" => 0xBE,
        "/" | "slash" => 0xBF,
        ";" | "semicolon" => 0xBA,
        "`" | "backtick" => 0xC0,
        "[" => 0xDB,
        "]" => 0xDD,
        "\\" => 0xDC,
        "'" | "quote" => 0xDE,
        s if s.len() == 1 => {
            let c = s.as_bytes()[0];
            if c.is_ascii_lowercase() {
                c.to_ascii_uppercase() as u16
            } else if c.is_ascii_digit() {
                c as u16
            } else {
                return None;
            }
        }
        s if s.starts_with('f') && s.len() <= 3 => {
            let num: u16 = s[1..].parse().ok()?;
            if (1..=24).contains(&num) {
                0x70 + (num - 1)
            } else {
                return None;
            }
        }
        _ => return None,
    };
    Some(vk)
}

pub fn parse_combo(combo: &str) -> Result<Vec<u16>, String> {
    let parts: Vec<&str> = combo.split('+').filter(|p| !p.trim().is_empty()).collect();
    if parts.is_empty() {
        return Err("empty hotkey".into());
    }
    parts
        .iter()
        .map(|p| vk_for(p).ok_or_else(|| format!("unknown key '{}'", p.trim())))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_combo() {
        assert_eq!(
            parse_combo("ctrl+shift+esc").unwrap(),
            vec![0x11, 0x10, 0x1B]
        );
        assert_eq!(parse_combo("alt+F4").unwrap(), vec![0x12, 0x73]);
        assert_eq!(parse_combo("win+d").unwrap(), vec![0x5B, 0x44]);
    }

    #[test]
    fn rejects_unknown() {
        assert!(parse_combo("ctrl+banana").is_err());
        assert!(parse_combo("").is_err());
    }
}
