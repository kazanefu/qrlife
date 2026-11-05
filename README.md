# QR GameOfLife

QRコードを読み取り、その内容をライフゲームの初期配置としてシミュレーションするRust製CLIです。

## 開発環境

Rust stable（`cargo` / `rustc`）で動作します。画像の読み込みは `image`、QRコードの検出・復号は `rqrr` に分離しています。Android/eguiは、CLIのコア仕様が固まった後に同じライブラリを利用する形で追加します。

## 実行

```text
cargo run -- path/to/qr.png --steps 10
```

検出したQRコードのモジュール（黒白セル）そのものを初期配置として使います。標準出力には復号した内容、初期状態と各ステップの盤面が表示されます。

対応画像形式はPNG、JPEG、GIF、BMP、WebPです。
