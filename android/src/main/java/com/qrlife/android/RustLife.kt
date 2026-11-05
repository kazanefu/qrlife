package com.qrlife.android

class RustLife private constructor(
    private var handle: Long,
    private val recreate: () -> RustLife?
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
        return recreate()
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
            return if (handle == 0L) null else RustLife(handle) { create(payload) }
        }

        fun createFromImage(bytes: ByteArray): RustLife? {
            val handle = nativeCreateFromImage(bytes)
            return if (handle == 0L) null else RustLife(handle) { createFromImage(bytes) }
        }

        fun createFromGray(width: Int, height: Int, bytes: ByteArray): RustLife? {
            val handle = nativeCreateFromGray(width, height, bytes)
            return if (handle == 0L) null else RustLife(handle) {
                createFromGray(width, height, bytes)
            }
        }

        @JvmStatic private external fun nativeCreate(payload: String): Long
        @JvmStatic private external fun nativeCreateFromImage(bytes: ByteArray): Long
        @JvmStatic private external fun nativeCreateFromGray(
            width: Int,
            height: Int,
            bytes: ByteArray
        ): Long
        @JvmStatic private external fun nativeStep(handle: Long)
        @JvmStatic private external fun nativeState(handle: Long): String
        @JvmStatic private external fun nativeWidth(handle: Long): Int
        @JvmStatic private external fun nativeHeight(handle: Long): Int
        @JvmStatic private external fun nativeDestroy(handle: Long)
    }
}
