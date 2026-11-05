use jni::{
    objects::{JClass, JString},
    sys::{jint, jlong, jstring},
    JNIEnv,
};
use qrlife::life::GameOfLife;
use std::ptr;

fn board_from_handle<'a>(handle: jlong) -> Option<&'a mut GameOfLife> {
    if handle == 0 {
        None
    } else {
        Some(unsafe { &mut *(handle as *mut GameOfLife) })
    }
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
