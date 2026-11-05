package com.qrlife.android

class RustLife private constructor(
    private var handle: Long,
    private val payload: String
) {
    val width: Int get() = nativeWidth(handle)
    val height: Int get() = nativeHeight(handle)
    val cells: List<Boolean>
        get() = nativeState(handle).map { it == '1' }

    fun step() {
        nativeStep(handle)
    }

    fun reset(): RustLife? {
        close()
        return create(payload)
    }

    fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("qrlife_android")
        }

        fun create(payload: String): RustLife? {
            val handle = nativeCreate(payload)
            return if (handle == 0L) null else RustLife(handle, payload)
        }

        @JvmStatic private external fun nativeCreate(payload: String): Long
        @JvmStatic private external fun nativeStep(handle: Long)
        @JvmStatic private external fun nativeState(handle: Long): String
        @JvmStatic private external fun nativeWidth(handle: Long): Int
        @JvmStatic private external fun nativeHeight(handle: Long): Int
        @JvmStatic private external fun nativeDestroy(handle: Long)
    }
}
