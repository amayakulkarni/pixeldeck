//! Generates the tray / window icon at runtime so the exe needs no asset files.

/// RGBA pixels (size * size * 4): a magenta disc with a white dot, transparent outside.
pub fn rgba(size: u32) -> Vec<u8> {
    let s = size as f32;
    let centre = (s - 1.0) / 2.0;
    let outer = s / 2.0 - 0.5;
    let inner = s * 0.18;
    let mut out = Vec::with_capacity((size * size * 4) as usize);
    for y in 0..size {
        for x in 0..size {
            let d = ((x as f32 - centre).powi(2) + (y as f32 - centre).powi(2)).sqrt();
            let px = if d <= inner {
                [255, 255, 255, 255]
            } else if d <= outer {
                [255, 38, 124, 255]
            } else {
                [0, 0, 0, 0]
            };
            out.extend_from_slice(&px);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn has_expected_length_and_shape() {
        let px = rgba(32);
        assert_eq!(px.len(), 32 * 32 * 4);
        // corner is transparent, centre is white
        assert_eq!(px[3], 0);
        let mid = (16 * 32 + 16) * 4;
        assert_eq!(&px[mid..mid + 4], &[255, 255, 255, 255]);
    }
}
