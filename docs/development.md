# 開発手順

## 構成

- ルートの`qrlife`: CLIと共有Rustコア
- `android/native`: Android向けJNIライブラリ。ライフゲームの状態更新はRustで行う
- `android`: CameraX、Compose、JNIのAndroidアプリ層
- `scripts/build-android.ps1`: Android用Rustライブラリ生成とDebug APKビルド

CLIは次で実行します。

```powershell
cargo run -- "C:\path\to\qr.png" --steps 10
```

## 環境構築

必要なもの:

- Rust stable
- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35
- Android NDK 27.2.12479018
- `cargo-ndk`

Android Studioをインストールした後、SDK Managerで上記SDK/NDKを導入します。Rust側は次で準備できます。

```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk
```

## ビルドとテスト

```powershell
cargo fmt --all
cargo test --workspace
.\scripts\build-android.ps1
```

APKは`android/build/outputs/apk/debug/android-debug.apk`に生成されます。`jniLibs`は生成物なのでGit管理しません。

## 実機接続

端末の開発者向けオプションとUSBデバッグを有効にし、USB接続後に端末側の「このPCを許可」を承認します。

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r android\build\outputs\apk\debug\android-debug.apk
```

`unauthorized`の場合は端末のUSBデバッグ認証ダイアログを承認し、表示されない場合はUSB接続を解除して再接続します。

## 引き継ぎ時の確認

1. `git status`で生成物が未追跡になっていないことを確認する。
2. `cargo test --workspace`を実行する。
3. `.\scripts\build-android.ps1`でAPKを再生成する。
4. QR画像入力では、Step 0がQRの黒白モジュール行列と一致することを確認する。

## 実装済みの操作

- カメラ / 画像ファイルからQRモジュール行列を読み込み
- 再生 / 一時停止 / リセット / 世代シーク
- シミュレーション速度調整
- QRサイズを下限とした盤面拡張
- 生存、死滅、誕生、死亡セルのRGBA設定
- 背景画像の設定
- 背景画像は縦横比を維持して盤面いっぱいに表示し、余分な部分を中央から切り取る
- GIF / MP4出力（出力ダイアログでステップ数を指定）
- 使い方ダイアログ
- 起動画面への復帰
