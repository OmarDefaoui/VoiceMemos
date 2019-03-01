package com.nordef.voicememos.encoders

// based on http://soundfile.sapp.org/doc/WaveFormat/

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FormatWAV(info: EncoderInfo, out: File) : Encoder {
    internal var NumSamples: Int = 0
    var info: EncoderInfo
        internal set
    internal var BytesPerSample: Int = 0
    internal var outFile: RandomAccessFile

    internal var order = ByteOrder.LITTLE_ENDIAN

    init {
        this.info = info
        NumSamples = 0

        BytesPerSample = info.bps / 8

        try {
            outFile = RandomAccessFile(out, "rw")
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        save()
    }

    fun save() {
        val SubChunk1Size = 16
        val SubChunk2Size = NumSamples * info.channels * BytesPerSample
        val ChunkSize = 4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)

        write("RIFF", ByteOrder.BIG_ENDIAN)
        write(ChunkSize, order)
        write("WAVE", ByteOrder.BIG_ENDIAN)

        val ByteRate = info.sampleRate * info.channels * BytesPerSample
        val AudioFormat: Short = 1 // PCM = 1 (i.e. Linear quantization)
        val BlockAlign = BytesPerSample * info.channels

        write("fmt ", ByteOrder.BIG_ENDIAN)
        write(SubChunk1Size, order)
        write(AudioFormat, order) //short
        write(info.channels.toShort(), order) // short
        write(info.sampleRate, order)
        write(ByteRate, order)
        write(BlockAlign.toShort(), order) // short
        write(info.bps.toShort(), order) // short

        write("data", ByteOrder.BIG_ENDIAN)
        write(SubChunk2Size, order)
    }

    internal fun write(str: String, order: ByteOrder) {
        try {
            val cc = str.toByteArray(charset("UTF-8"))
            val bb = ByteBuffer.allocate(cc.size)
            bb.order(order)
            bb.put(cc)
            bb.flip()

            outFile.write(bb.array())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    internal fun write(i: Int, order: ByteOrder) {
        val bb = ByteBuffer.allocate(Integer.SIZE / java.lang.Byte.SIZE)
        bb.order(order)
        bb.putInt(i)
        bb.flip()

        try {
            outFile.write(bb.array())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    internal fun write(i: Short, order: ByteOrder) {
        val bb = ByteBuffer.allocate(java.lang.Short.SIZE / java.lang.Byte.SIZE)
        bb.order(order)
        bb.putShort(i)
        bb.flip()

        try {
            outFile.write(bb.array())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    override fun encode(buf: ShortArray) {
        NumSamples += buf.size / info.channels
        try {
            val bb = ByteBuffer.allocate(buf.size * (java.lang.Short.SIZE / java.lang.Byte.SIZE))
            bb.order(order)
            for (i in buf.indices)
                bb.putShort(buf[i])
            bb.flip()
            outFile.write(bb.array())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    override fun close() {
        try {
            outFile.seek(0)
            save()
            outFile.close()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

}