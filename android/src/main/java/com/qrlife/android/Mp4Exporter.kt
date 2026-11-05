package com.qrlife.android

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.nio.ByteBuffer

object Mp4Exporter {
    fun export(
        context: Context,
        uri: Uri,
        board: RustLife,
        steps: Int,
        cellSize: Int,
        alive: Int,
        dead: Int
    ) {
        val width = board.width * cellSize
        val height = board.height * cellSize
        require(width % 2 == 0 && height % 2 == 0) { "MP4の盤面サイズは偶数が必要です" }
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 21)
        format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 8)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 8)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "w")
            ?: error("出力先を開けませんでした")
        val muxer = MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var muxing = false
        fun drain(end: Boolean) {
            while (true) {
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER && !end -> return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    index >= 0 -> {
                        val output = codec.getOutputBuffer(index) ?: continue
                        if (info.size > 0 && muxing) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            muxer.writeSampleData(track, output, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        }
        val original = board.generation
        try {
            for (frame in 0..steps) {
                board.seek(frame)
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val input = codec.getInputBuffer(inputIndex)!!
                    input.clear()
                    input.put(toNv12(board.cells, board.width, board.height, cellSize, alive, dead))
                    codec.queueInputBuffer(inputIndex, 0, input.position(), frame * 125_000L, 0)
                }
                drain(false)
            }
            val endIndex = codec.dequeueInputBuffer(10_000)
            if (endIndex >= 0) {
                codec.queueInputBuffer(
                    endIndex,
                    0,
                    0,
                    (steps + 1) * 125_000L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drain(true)
        } finally {
            board.seek(original)
            codec.stop()
            codec.release()
            if (muxing) muxer.stop()
            muxer.release()
            descriptor.close()
        }
    }

    private fun toNv12(
        cells: List<Boolean>,
        boardWidth: Int,
        boardHeight: Int,
        cellSize: Int,
        alive: Int,
        dead: Int
    ): ByteArray {
        val width = boardWidth * cellSize
        val height = boardHeight * cellSize
        val output = ByteArray(width * height * 3 / 2)
        fun rgb(value: Boolean, shift: Int) =
            if (value) (alive shr shift) and 255 else (dead shr shift) and 255
        for (y in 0 until height) for (x in 0 until width) {
            val aliveCell = cells[(y / cellSize) * boardWidth + x / cellSize]
            val r = rgb(aliveCell, 16)
            val g = rgb(aliveCell, 8)
            val b = rgb(aliveCell, 0)
            output[y * width + x] = (0.257 * r + 0.504 * g + 0.098 * b + 16).toInt().toByte()
            if (y % 2 == 0 && x % 2 == 0) {
                val offset = width * height + (y / 2) * width + x
                output[offset] = (-0.148 * r - 0.291 * g + 0.439 * b + 128).toInt().toByte()
                output[offset + 1] = (0.439 * r - 0.368 * g - 0.071 * b + 128).toInt().toByte()
            }
        }
        return output
    }
}
