use std::{env, process};

use qrlife::{life::GameOfLife, qr};

fn usage() {
    eprintln!("使い方: qrlife <画像ファイル> [--steps <回数>]");
    eprintln!("  検出したQRコードの黒白モジュールを初期盤面として使用します。");
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 || matches!(args.get(1).map(String::as_str), Some("--help" | "-h")) {
        usage();
        if args.len() < 2 {
            process::exit(2);
        }
        return;
    }

    let image_path = &args[1];
    let mut steps = 10usize;
    let mut index = 2;
    while index < args.len() {
        match args[index].as_str() {
            "--steps" => {
                index += 1;
                if index >= args.len() {
                    eprintln!("--stepsには数値が必要です");
                    process::exit(2);
                }
                steps = args[index].parse().unwrap_or_else(|_| {
                    eprintln!("ステップ数が不正です: {}", args[index]);
                    process::exit(2);
                });
            }
            "--help" | "-h" => {
                usage();
                return;
            }
            option => {
                eprintln!("不明な引数です: {option}");
                usage();
                process::exit(2);
            }
        }
        index += 1;
    }

    let decoded = qr::decode_image(image_path).unwrap_or_else(|error| {
        eprintln!("エラー: {error}");
        process::exit(1);
    });
    let mut game = GameOfLife::from_cells(decoded.width, decoded.height, decoded.cells)
        .unwrap_or_else(|error| {
            eprintln!("エラー: {error}");
            process::exit(1);
        });

    println!("QR内容: {:?}", decoded.payload);
    for step in 0..=steps {
        println!(
            "\nStep {step} (alive: {})\n{}",
            game.alive_count(),
            game.render()
        );
        if step < steps {
            game.step();
        }
    }
}
