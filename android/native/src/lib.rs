use jni::{
    objects::{JClass, JString},
    sys::{jint, jlong, jstring},
    JNIEnv,
};
use qrlife::{life::GameOfLife, qr::DecodedQr};
use std::io::Cursor;
use std::ptr;

fn board_from_handle<'a>(handle: jlong) -> Option<&'a mut GameOfLife> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &mut *(handle as *mut GameOfLife) })
    }
}

fn board_from_decoded(decoded: DecodedQr) -> jlong {
    GameOfLife::from_cells(decoded.width, decoded.height, decoded.cells)
        .map(|board| Box::into_raw(Box::new(board)) as jlong)
        .unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeCreate(
    mut env: JNIEnv,
    _class: JClass,
    payload: JString,
) -> jlong {
    let payload: String = match env.get_string(&payload) {
        Ok(value) => value.into(),
        Err(_) => return 0,
    };
    let bytes = payload.as_bytes();
    if bytes.is_empty() {
        return 0;
    }
    let cells = bytes
        .iter()
        .flat_map(|byte| (0..8).map(move |bit| byte & (1 << (7 - bit)) != 0))
        .collect::<Vec<_>>();
    match GameOfLife::from_cells(8, bytes.len(), cells) {
        Ok(board) => Box::into_raw(Box::new(board)) as jlong,
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeCreateFromImage(
    mut env: JNIEnv,
    _class: JClass,
    bytes: jni::objects::JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&bytes) {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };
    let image = match image::load_from_memory(&bytes) {
        Ok(image) => image.to_luma8(),
        Err(_) => return 0,
    };
    match qrlife::qr::decode_gray_image(&image) {
        Ok(decoded) => board_from_decoded(decoded),
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeCreateFromGray(
    mut env: JNIEnv,
    _class: JClass,
    width: jint,
    height: jint,
    bytes: jni::objects::JByteArray,
) -> jlong {
    let bytes = match env.convert_byte_array(&bytes) {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };
    let image = match image::GrayImage::from_raw(width as u32, height as u32, bytes) {
        Some(image) => image,
        None => return 0,
    };
    match qrlife::qr::decode_gray_image(&image) {
        Ok(decoded) => board_from_decoded(decoded),
        Err(_) => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeStep(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(board) = board_from_handle(handle) {
        board.step();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeReset(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(board) = board_from_handle(handle) {
        board.reset();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeSeek(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    generation: jint,
) {
    if let Some(board) = board_from_handle(handle) {
        board.seek(generation.max(0) as usize);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeResize(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    width: jint,
    height: jint,
) -> jint {
    board_from_handle(handle).map_or(-1, |board| {
        board
            .resize(width as usize, height as usize)
            .map_or(-1, |_| 0)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeState(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let Some(board) = board_from_handle(handle) else {
        return ptr::null_mut();
    };
    let mut state = String::with_capacity(board.width() * board.height());
    for y in 0..board.height() {
        for x in 0..board.width() {
            state.push(if board.get(x, y) { '1' } else { '0' });
        }
    }
    match env.new_string(state) {
        Ok(value) => value.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeWidth(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    board_from_handle(handle).map_or(0, |board| board.width() as jint)
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeHeight(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    board_from_handle(handle).map_or(0, |board| board.height() as jint)
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeGeneration(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    board_from_handle(handle).map_or(0, |board| board.generation() as jint)
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeGif(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    steps: jint,
    cell_size: jint,
    alive: jint,
    dead: jint,
) -> jni::sys::jbyteArray {
    let Some(board) = board_from_handle(handle) else {
        return ptr::null_mut();
    };
    let cell_size = cell_size.max(1) as usize;
    let width = board.width() * cell_size;
    let height = board.height() * cell_size;
    if width > u16::MAX as usize || height > u16::MAX as usize {
        return ptr::null_mut();
    }
    let mut output = Cursor::new(Vec::new());
    let palette = [
        ((alive >> 16) as u8),
        ((alive >> 8) as u8),
        (alive as u8),
        ((dead >> 16) as u8),
        ((dead >> 8) as u8),
        (dead as u8),
    ];
    let mut encoder = match gif::Encoder::new(&mut output, width as u16, height as u16, &palette) {
        Ok(encoder) => encoder,
        Err(_) => return ptr::null_mut(),
    };
    let mut frame_board = board.clone();
    for _ in 0..=steps.max(0) {
        let mut pixels = vec![0u8; width * height];
        for y in 0..board.height() {
            for x in 0..board.width() {
                let value = if frame_board.get(x, y) { 0 } else { 1 };
                for py in 0..cell_size {
                    for px in 0..cell_size {
                        pixels[(y * cell_size + py) * width + x * cell_size + px] = value;
                    }
                }
            }
        }
        let mut frame = gif::Frame::default();
        frame.width = width as u16;
        frame.height = height as u16;
        frame.buffer = pixels.into();
        frame.delay = 6;
        if encoder.write_frame(&frame).is_err() {
            return ptr::null_mut();
        }
        frame_board.step();
    }
    drop(encoder);
    match env.byte_array_from_slice(&output.into_inner()) {
        Ok(array) => array.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_qrlife_android_RustLife_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        unsafe {
            drop(Box::from_raw(handle as *mut GameOfLife));
        }
    }
}
