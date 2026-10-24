//! Keyboard injection. Real implementation on Windows (SendInput), logging stub elsewhere
//! so the crate still builds and the protocol can be tested on Linux/macOS.

use crate::keys::parse_combo;

/// Press all keys in order, then release in reverse order.
pub fn hotkey(combo: &str) -> Result<(), String> {
    let vks = parse_combo(combo)?;
    for vk in &vks {
        imp::key(*vk, false);
    }
    for vk in vks.iter().rev() {
        imp::key(*vk, true);
    }
    Ok(())
}

pub fn type_text(text: &str) {
    for unit in text.encode_utf16() {
        imp::unicode(unit, false);
        imp::unicode(unit, true);
    }
}

#[cfg(windows)]
mod imp {
    use windows::Win32::UI::Input::KeyboardAndMouse::*;

    fn send(input: INPUT) {
        unsafe {
            SendInput(&[input], std::mem::size_of::<INPUT>() as i32);
        }
    }

    fn is_extended(vk: u16) -> bool {
        matches!(vk, 0x21..=0x28 | 0x2D | 0x2E | 0x5B | 0xAD..=0xB3)
    }

    pub fn key(vk: u16, up: bool) {
        let mut flags = if up {
            KEYEVENTF_KEYUP
        } else {
            KEYBD_EVENT_FLAGS(0)
        };
        if is_extended(vk) {
            flags |= KEYEVENTF_EXTENDEDKEY;
        }
        send(INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: VIRTUAL_KEY(vk),
                    wScan: 0,
                    dwFlags: flags,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        });
    }

    pub fn unicode(unit: u16, up: bool) {
        let mut flags = KEYEVENTF_UNICODE;
        if up {
            flags |= KEYEVENTF_KEYUP;
        }
        send(INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: VIRTUAL_KEY(0),
                    wScan: unit,
                    dwFlags: flags,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        });
    }
}

#[cfg(not(windows))]
mod imp {
    pub fn key(vk: u16, up: bool) {
        println!(
            "[stub] key vk=0x{vk:02X} {}",
            if up { "up" } else { "down" }
        );
    }
    pub fn unicode(unit: u16, up: bool) {
        println!(
            "[stub] unicode u+{unit:04X} {}",
            if up { "up" } else { "down" }
        );
    }
}
