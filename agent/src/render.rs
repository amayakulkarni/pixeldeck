//! Platform-independent drawing of the pairing QR code into a 0x00RRGGBB pixel buffer.

pub struct Qr {
    /// Modules per side.
    pub size: usize,
    /// Row-major, `true` = dark module.
    pub modules: Vec<bool>,
}

const BACKGROUND: u32 = 0x00FF_FFFF;
const FRAME: u32 = 0x00FF_267C; // PixelDeck magenta
const DARK: u32 = 0x0000_0000;
/// Quiet zone around the code, in modules (the QR spec asks for 4).
const QUIET: usize = 4;

/// Paint a white window with a magenta frame and, if available, the QR code centred inside it.
pub fn paint(buf: &mut [u32], width: usize, height: usize, qr: Option<&Qr>) {
    if width == 0 || height == 0 || buf.len() < width * height {
        return;
    }
    let frame = 6.min(width / 2).min(height / 2);
    for y in 0..height {
        for x in 0..width {
            let on_frame = x < frame || y < frame || x >= width - frame || y >= height - frame;
            buf[y * width + x] = if on_frame { FRAME } else { BACKGROUND };
        }
    }
    let Some(qr) = qr else { return };
    if qr.size == 0 || qr.modules.len() != qr.size * qr.size {
        return;
    }
    let total = qr.size + 2 * QUIET;
    let available = width.min(height).saturating_sub(2 * frame);
    let cell = available / total;
    if cell == 0 {
        return;
    }
    let side = cell * total;
    let origin_x = (width - side) / 2 + cell * QUIET;
    let origin_y = (height - side) / 2 + cell * QUIET;
    for my in 0..qr.size {
        for mx in 0..qr.size {
            if !qr.modules[my * qr.size + mx] {
                continue;
            }
            for dy in 0..cell {
                let row = (origin_y + my * cell + dy) * width + origin_x + mx * cell;
                for px in &mut buf[row..row + cell] {
                    *px = DARK;
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn checker(n: usize) -> Qr {
        Qr {
            size: n,
            modules: (0..n * n).map(|i| i % 2 == 0).collect(),
        }
    }

    #[test]
    fn paints_frame_and_modules() {
        let (w, h) = (400, 440);
        let mut buf = vec![1u32; w * h];
        paint(&mut buf, w, h, Some(&checker(25)));
        assert_eq!(buf[0], FRAME);
        assert_eq!(buf[(h - 1) * w + (w - 1)], FRAME);
        assert_eq!(buf[10 * w + 10], BACKGROUND);
        assert!(buf.iter().any(|&p| p == DARK));
    }

    #[test]
    fn tiny_window_does_not_panic() {
        for (w, h) in [(0, 0), (1, 1), (7, 7), (30, 30), (400, 3)] {
            let mut buf = vec![0u32; w * h];
            paint(&mut buf, w, h, Some(&checker(25)));
        }
    }

    #[test]
    fn works_without_a_code() {
        let mut buf = vec![0u32; 50 * 50];
        paint(&mut buf, 50, 50, None);
        assert_eq!(buf[25 * 50 + 25], BACKGROUND);
    }

    #[test]
    fn rejects_inconsistent_code() {
        let mut buf = vec![0u32; 100 * 100];
        let bad = Qr {
            size: 5,
            modules: vec![true; 3],
        };
        paint(&mut buf, 100, 100, Some(&bad));
        assert!(buf.iter().all(|&p| p != DARK));
    }
}
