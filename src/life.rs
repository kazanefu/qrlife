#[derive(Clone, Debug, PartialEq, Eq)]
pub struct GameOfLife {
    width: usize,
    height: usize,
    cells: Vec<bool>,
    initial: Vec<bool>,
    generation: usize,
    min_width: usize,
    min_height: usize,
}

impl GameOfLife {
    pub fn new(width: usize, height: usize) -> Result<Self, String> {
        if width == 0 || height == 0 {
            return Err("盤面の幅と高さは1以上である必要があります".into());
        }
        let size = width
            .checked_mul(height)
            .ok_or_else(|| "盤面が大きすぎます".to_string())?;
        Ok(Self {
            width,
            height,
            cells: vec![false; size],
            initial: vec![false; size],
            generation: 0,
            min_width: width,
            min_height: height,
        })
    }

    pub fn from_cells(width: usize, height: usize, cells: Vec<bool>) -> Result<Self, String> {
        if cells.len() != width.saturating_mul(height) {
            return Err("セル数が盤面サイズと一致しません".into());
        }
        Ok(Self {
            width,
            height,
            initial: cells.clone(),
            cells,
            generation: 0,
            min_width: width,
            min_height: height,
        })
    }

    pub fn width(&self) -> usize {
        self.width
    }

    pub fn height(&self) -> usize {
        self.height
    }

    pub fn generation(&self) -> usize {
        self.generation
    }

    pub fn get(&self, x: usize, y: usize) -> bool {
        x < self.width && y < self.height && self.cells[y * self.width + x]
    }

    pub fn set(&mut self, x: usize, y: usize, alive: bool) {
        if x < self.width && y < self.height {
            self.cells[y * self.width + x] = alive;
        }
    }

    pub fn alive_count(&self) -> usize {
        self.cells.iter().filter(|&&cell| cell).count()
    }

    pub fn step(&mut self) {
        let mut next = vec![false; self.cells.len()];
        for y in 0..self.height {
            for x in 0..self.width {
                let neighbors = self.neighbor_count(x, y);
                next[y * self.width + x] = neighbors == 3 || (self.get(x, y) && neighbors == 2);
            }
        }
        self.cells = next;
        self.generation += 1;
    }

    pub fn reset(&mut self) {
        self.cells.clone_from(&self.initial);
        self.generation = 0;
    }

    pub fn seek(&mut self, generation: usize) {
        self.reset();
        for _ in 0..generation {
            self.step();
        }
    }

    pub fn resize(&mut self, width: usize, height: usize) -> Result<(), String> {
        if width < self.min_width || height < self.min_height {
            return Err("盤面はQRコードより小さくできません".into());
        }
        let offset_x = (width - self.width) / 2;
        let offset_y = (height - self.height) / 2;
        let mut next = vec![false; width * height];
        let mut next_initial = vec![false; width * height];
        for y in 0..self.height {
            for x in 0..self.width {
                let target = (y + offset_y) * width + x + offset_x;
                next[target] = self.get(x, y);
                next_initial[target] = self.initial[y * self.width + x];
            }
        }
        self.width = width;
        self.height = height;
        self.cells = next;
        self.initial = next_initial;
        self.generation = 0;
        Ok(())
    }

    pub fn render(&self) -> String {
        (0..self.height)
            .map(|y| {
                (0..self.width)
                    .map(|x| if self.get(x, y) { '█' } else { '·' })
                    .collect::<String>()
            })
            .collect::<Vec<_>>()
            .join("\n")
    }

    fn neighbor_count(&self, x: usize, y: usize) -> usize {
        let mut count = 0;
        for dy in -1..=1 {
            for dx in -1..=1 {
                if dx == 0 && dy == 0 {
                    continue;
                }
                let nx = x as isize + dx;
                let ny = y as isize + dy;
                if nx >= 0
                    && (nx as usize) < self.width
                    && ny >= 0
                    && (ny as usize) < self.height
                    && self.get(nx as usize, ny as usize)
                {
                    count += 1;
                }
            }
        }
        count
    }
}

#[cfg(test)]
mod tests {
    use super::GameOfLife;

    #[test]
    fn blinker_changes_orientation() {
        let mut game = GameOfLife::new(5, 5).unwrap();
        for y in 1..=3 {
            game.set(2, y, true);
        }
        game.step();
        assert!(game.get(1, 2) && game.get(2, 2) && game.get(3, 2));
        assert_eq!(game.alive_count(), 3);
    }

    #[test]
    fn reset_and_seek_are_deterministic() {
        let mut cells = vec![false; 25];
        cells[2 * 5 + 2] = true;
        cells[1 * 5 + 2] = true;
        cells[3 * 5 + 2] = true;
        let mut game = GameOfLife::from_cells(5, 5, cells).unwrap();
        game.step();
        assert_eq!(game.generation(), 1);
        game.reset();
        assert_eq!(game.generation(), 0);
        game.seek(1);
        assert_eq!(game.generation(), 1);
        assert!(game.get(1, 2) && game.get(2, 2) && game.get(3, 2));
    }

    #[test]
    fn resize_preserves_qr_minimum_and_initial_state() {
        let mut cells = vec![false; 9];
        cells[4] = true;
        let mut game = GameOfLife::from_cells(3, 3, cells).unwrap();
        assert!(game.resize(5, 5).is_ok());
        assert!(game.resize(2, 2).is_err());
        game.reset();
        assert!(game.get(2, 2));
    }
}
