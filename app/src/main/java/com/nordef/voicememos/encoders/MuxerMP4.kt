package com.nordef.voicememos.encoders

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

@TargetApi(21)
open class MuxerMP4 : Encoder {
    lateinit var info: EncoderInfo
        internal set
    internal lateinit var encoder: MediaCodec
    internal lateinit var muxer: MediaMuxer
    internal var audioTrackIndex: Int = 0
    internal var NumSamples: Long = 0

    internal val currentTimeStamp: Long
        get() = NumSamples * 1000 * 1000 / info.sampleRate

    fun create(info: EncoderInfo, format: MediaFormat, out: File) {
        this.info = info

        try {
            encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME))
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    override fun encode(buf: ShortArray) {
        var offset = 0
        while (offset < buf.size) {
            var len = buf.size - offset

            val inputIndex = encoder.dequeueInputBuffer(-1)
            if (inputIndex < 0)
                throw RuntimeException("unable to open encoder input buffer")

            val input = encoder.getInputBuffer(inputIndex)
            input!!.clear()

            len = Math.min(len, input.limit() / 2)

            for (i in 0 until len)
                input.putShort(buf[i])

            val bytes = len * 2

            val ts = currentTimeStamp
            encoder.queueInputBuffer(inputIndex, 0, bytes, ts, 0)
            NumSamples += (len / info.channels).toLong()
            offset += len

            while (encode())
            ;// do encode()
        }
    }

    internal fun encode(): Boolean {
        val outputInfo = MediaCodec.BufferInfo()
        val outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0)
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
            return false

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioTrackIndex = muxer.addTrack(encoder.outputFormat)
            muxer.start()
        }

        if (outputIndex >= 0) {
            val output = encoder.getOutputBuffer(outputIndex)
            output!!.position(outputInfo.offset)
            output.limit(outputInfo.offset + outputInfo.size)

            muxer.writeSampleData(audioTrackIndex, output, outputInfo)

            encoder.releaseOutputBuffer(outputIndex, false)
        }

        return true
    }

    override fun close() {
        end()
        encode()

        encoder.stop()
        encoder.release()

        muxer.stop()
        muxer.release()
    }

    internal fun end() {
        val inputIndex = encoder.dequeueInputBuffer(-1)
        if (inputIndex >= 0) {
            val input = encoder.getInputBuffer(inputIndex)
            input!!.clear()
            encoder.queueInputBuffer(inputIndex, 0, 0, currentTimeStamp, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

}