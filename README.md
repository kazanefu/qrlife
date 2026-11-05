# QR GameOfLife

QRコードを読み取り、その内容をライフゲームの初期配置としてシミュレーションするRust製CLIです。

## 開発環境

Rust stable（`cargo` / `rustc`）で動作します。画像の読み込みは `image`、QRコードの検出・復号は `rqrr` に分離しています。CLIはルートパッケージとして保持し、Android JNIライブラリは `android/native` のworkspace memberとして分離しています。

## 実行

```text
cargo run -- path/to/qr.png --steps 10
```

検出したQRコードのモジュール（黒白セル）そのものを初期配置として使います。標準出力には復号した内容、初期状態と各ステップの盤面が表示されます。

対応画像形式はPNG、JPEG、GIF、BMP、WebPです。

## Androidアプリ

`android/` にCameraXのカメラ/画像入力と、Rust JNIライブラリによるQR検出・ライフゲーム実行を組み合わせたAndroidアプリを追加しています。Kotlin側はAndroidライフサイクル、CameraX、Composeの画面シェルを担当し、QR検出・セル状態・世代更新はRust側で実行します。

必要な環境はJDK 17、Android SDK Platform 35、Build Tools 35、NDK 27.2.12479018、Rust stable、`cargo-ndk`です。Android Studioでプロジェクトルートを開くか、SDKとJDKを設定した上でプロジェクトルートから次を実行します。

```text
.\scripts\build-android.ps1
```

Android版のQR入力も画像画素をRustの`rqrr`へ渡し、検出したQRモジュール行列を初期配置にします。CLIのRust実装はそのまま残してあります。

`cargo ndk`が生成するABI別ライブラリは`android/src/main/jniLibs`へ配置されます。接続デバイスがある場合は、ビルド後にAndroid Studioまたは`adb install android/build/outputs/apk/debug/android-debug.apk`で検証できます。

draft.mdにある「eguiでRust UI、KotlinはCameraXのみ」という最終形にはまだ到達していません。現在のCompose UIはAndroid起動と実機検証を先に成立させるための段階実装で、次段階でegui/winitまたはAndroid ViewへのRust描画移行を行います。
