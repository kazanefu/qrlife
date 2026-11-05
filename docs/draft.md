# 草案

## 概要

Androidアプリケーションで(いずれはWindows向けにもビルドするかも)、QRコードを読み取り、それをライフゲームの初期配置としてシミュレーションするアプリケーションです。

## 機能

1. スタートボタンを押すと、カメラが起動し、QRコードを読み取ることができます。(画像ファイルからの読み取りも可能です。)
2. 読み取ったQRコードの内容をライフゲームの初期配置とする。
3. Playボタンを押すと、ライフゲームのシミュレーションが開始されます。
4. Pauseボタンを押すと、シミュレーションが一時停止します。
5. Resetボタンを押すと、シミュレーションが初期状態に戻ります。
6. シークバーを操作することで、シミュレーションのステップ数を変更することができます。
7. シミュレーションの速度を調整することができます。
8. ライフゲームの盤面のサイズを変更することができます。(QRコードのサイズより小さくすることはできません。また、デフォルトではQRコードのサイズと同じサイズに設定されます。)
9. シミュレーションの結果を保存することができます。(mp4 or gif形式)(出力ボタンを押すと、ステップ数と出力形式を選択するダイアログが表示されます。) 
10. 生きてるセルの色と死んでるセルの色を変更することができます。(rgba,デフォルトでは、生きてるセルは緑色、死んでるセルは黒色に設定されています。この描画は、背景の描画の上から行われます。alpha値が最大でない場合は、背景の描画が透けて見えます。)
11. 新しく生まれたセルと新しく死んだセルの色を変更することができます。(rgba,デフォルトでは、両方透明に設定されています。)(この描画は10の描画の上から行われ、alpha値が最大でない場合は、10の描画が透けて見えます。)
12. 背景の画像を変更することができます。(デフォルトでは、背景は黒色に設定されています。)
13. 終了ボタンを押すとタイトル画面に戻ります。

## 技術選定

基本的にはRustでロジックとUIを実装し、カメラの起動はKotlinでCameraXを使用して実装する予定です。RustでのUIは、eguiを使用する予定です。

使用するクレート:

- android-activity
- winit
- wgpu
- egui
- image
- rqrr or qeircs

Kotlinの開発環境はまだ整っていないので環境構築から始めます。

## ディレクトリ構成

```
qr_life/
├── Cargo.toml
├── src/
│   ├── lib.rs
│   ├── life/
│   │   ├── mod.rs
│   │   └── simulation.rs
│   ├── qr/
│   │   ├── mod.rs
│   │   └── matrix.rs
│   └── app/
│       ├── mod.rs
│       └── state.rs
└── android/
    └── ...
```

## lifeモジュールのイメージ

```rust
struct GameOfLife {
    width: usize,
    height: usize,
    cells: Vec<bool>,
    prev_cells: Vec<bool>,
}

impl GameOfLife {
    fn new(width: usize, height: usize) -> Self {
        Self {
            width,
            height,
            cells: vec![false; width * height],
            prev_cells: vec![false; width * height],
        }
    }

    fn set_cell(&mut self, x: usize, y: usize, alive: bool) {
        if x < self.width && y < self.height {
            self.cells[y * self.width + x] = alive;
        }
    }

    fn update_prev_cells(&mut self) {
        self.prev_cells.copy_from_slice(&self.cells);
    }

    fn get_cell(&self, x: usize, y: usize) -> bool {
        if x < self.width && y < self.height {
            self.cells[y * self.width + x]
        } else {
            false
        }
    }

    fn get_prev_cell(&self, x: usize, y: usize) -> bool {
        if x < self.width && y < self.height {
            self.prev_cells[y * self.width + x]
        } else {
            false
        }
    }

    fn update(&mut self) {
        self.update_prev_cells();
        for y in 0..self.height {
            for x in 0..self.width {
                let alive_neighbors = self.count_prev_alive_neighbors(x, y);
                let is_alive = self.get_prev_cell(x, y);
                new_cells[y * self.width + x] = match (is_alive, alive_neighbors) {
                    (true, 2) | (true, 3) => true,
                    (false, 3) => true,
                    _ => false,
                };
            }
        }
        self.cells = new_cells;
    }

    fn count_alive_neighbors(&self, x: usize, y: usize) -> usize {
        let mut count = 0;
        for dy in -1..=1 {
            for dx in -1..=1 {
                if dx == 0 && dy == 0 {
                    continue;
                }
                let nx = x as isize + dx;
                let ny = y as isize + dy;
                if nx >= 0 && nx < self.width as isize && ny >= 0 && ny < self.height as isize {
                    if self.get_cell(nx as usize, ny as usize) {
                        count += 1;
                    }
                }
            }
        }
        count
    }

    fn count_prev_alive_neighbors(&self, x: usize, y: usize) -> usize {
        let mut count = 0;
        for dy in -1..=1 {
            for dx in -1..=1 {
                if dx == 0 && dy == 0 {
                    continue;
                }
                let nx = x as isize + dx;
                let ny = y as isize + dy;
                if nx >= 0 && nx < self.width as isize && ny >= 0 && ny < self.height as isize {
                    if self.get_prev_cell(nx as usize, ny as usize) {
                        count += 1;
                    }
                }
            }
        }
        count
    }
}
```
