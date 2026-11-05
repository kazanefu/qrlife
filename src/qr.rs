use image::GrayImage;
use rqrr::{BitGrid, PreparedImage};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DecodedQr {
    pub payload: String,
    pub width: usize,
    pub height: usize,
    pub cells: Vec<bool>,
}

pub fn decode_image(path: &str) -> Result<DecodedQr, String> {
    let image = image::open(path)
        .map_err(|error| format!("画像を開けませんでした: {error}"))?
        .to_luma8();
    decode_gray_image(&image)
}

pub fn decode_gray_image(image: &GrayImage) -> Result<DecodedQr, String> {
    let mut prepared = PreparedImage::prepare(image.clone());
    let grids = prepared.detect_grids();
    if grids.is_empty() {
        return Err("画像からQRコードを検出できませんでした".into());
    }

    let mut errors = Vec::new();
    for grid in grids {
        match grid.decode() {
            Ok((_metadata, content)) => {
                let size = grid.grid.size();
                let mut cells = Vec::with_capacity(size * size);
                for y in 0..size {
                    for x in 0..size {
                        cells.push(grid.grid.bit(y, x));
                    }
                }
                return Ok(DecodedQr {
                    payload: content,
                    width: size,
                    height: size,
                    cells,
                });
            }
            Err(error) => errors.push(error.to_string()),
        }
    }
    Err(format!(
        "QRコードを復号できませんでした: {}",
        errors.join("; ")
    ))
}

pub fn payload_to_cells(payload: &str) -> Result<(usize, usize, Vec<bool>), String> {
    let lines: Vec<&str> = payload
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .collect();

    if !lines.is_empty()
        && lines.iter().all(|line| {
            line.chars()
                .all(|cell| matches!(cell, '0' | '1' | '#' | 'X' | 'x' | '.' | '_'))
        })
    {
        let width = lines[0].chars().count();
        if width == 0 || lines.iter().any(|line| line.chars().count() != width) {
            return Err("QRコード内の盤面行の幅が一致しません".into());
        }
        let cells = lines
            .iter()
            .flat_map(|line| {
                line.chars()
                    .map(|cell| matches!(cell, '1' | '#' | 'X' | 'x'))
            })
            .collect();
        return Ok((width, lines.len(), cells));
    }

    if payload.is_empty() {
        return Err("QRコードの内容が空です".into());
    }
    let bytes = payload.as_bytes();
    let width = 8;
    let height = bytes.len();
    let mut cells = vec![false; width * height];
    for (index, byte) in bytes.iter().enumerate() {
        for bit in 0..8 {
            cells[index * 8 + bit] = byte & (1 << (7 - bit)) != 0;
        }
    }
    Ok((width, height, cells))
}

#[cfg(test)]
mod tests {
    use super::payload_to_cells;

    #[test]
    fn parses_text_grid() {
        assert_eq!(
            payload_to_cells(".#.\n###").unwrap(),
            (3, 2, vec![false, true, false, true, true, true])
        );
    }

    #[test]
    fn converts_regular_text_to_eight_bit_rows() {
        let (width, height, cells) = payload_to_cells("A").unwrap();
        assert_eq!((width, height), (8, 1));
        assert_eq!(
            cells,
            vec![false, true, false, false, false, false, false, true]
        );
    }

    #[test]
    fn converts_payload_longer_than_one_row_without_overflow() {
        let (width, height, cells) = payload_to_cells("ABCDE").unwrap();
        assert_eq!((width, height), (8, 5));
        assert_eq!(cells.len(), 40);
    }
}
