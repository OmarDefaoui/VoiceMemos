package com.nordef.voicememos.app

import android.media.AudioFormat

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class RawSamples(internal var `in`: File) {

    internal var `is`: InputStream? = null
    internal lateinit var readBuffer: ByteArray

    internal var os: OutputStream? = null

    val samples: Long
        get() = getSamples(`in`.length())

    // open for writing with specified offset to truncate file
    fun open(writeOffset: Long) {
        trunk(writeOffset)
        try {
            os = BufferedOutputStream(FileOutputStream(`in`, true))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    // open for reading
    //
    // bufReadSize - samples count
    fun open(bufReadSize: Int) {
        try {
            readBuffer = ByteArray(getBufferLen(bufReadSize.toLong()).toInt())
            `is` = FileInputStream(`in`)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    // open for read with initial offset and buffer read size
    //
    // offset - samples offset
    // bufReadSize - samples size
    fun open(offset: Long, bufReadSize: Int) {
        try {
            readBuffer = ByteArray(getBufferLen(bufReadSize.toLong()).toInt())
            `is` = FileInputStream(`in`)
            `is`!!.skip(offset * if (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 2 else 1)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun read(buf: ShortArray): Int {
        try {
            val len = `is`!!.read(readBuffer)
            if (len <= 0)
                return 0
            ByteBuffer.wrap(readBuffer, 0, len).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(buf, 0, getSamples(len.toLong()).toInt())
            return getSamples(len.toLong()).toInt()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun write(`val`: Short) {
        try {
            val bb = ByteBuffer.allocate(java.lang.Short.SIZE / java.lang.Byte.SIZE)
            bb.order(ByteOrder.BIG_ENDIAN)
            bb.putShort(`val`)
            os!!.write(bb.array(), 0, bb.limit())
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun write(buf: ShortArray) {
        for (i in buf.indices) {
            write(buf[i])
        }
    }

    fun trunk(pos: Long) {
        try {
            val outChan = FileOutputStream(`in`, true).channel
            outChan.truncate(getBufferLen(pos))
            outChan.close()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun close() {
        try {
            if (`is` != null)
                `is`!!.close()
            `is` = null

            if (os != null)
                os!!.close()
            os = null
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    companion object {
        var AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        var CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO

        // quite root gives me 20db
        var NOISE_DB = 20
        // max 90 dB detection for android mic
        var MAXIMUM_DB = 90

        fun getSamples(len: Long): Long {
            return len / if (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 2 else 1
        }

        fun getBufferLen(samples: Long): Long {
            return samples * if (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 2 else 1
        }

        fun getAmplitude(buffer: ShortArray, offset: Int, len: Int): Double {
            var sum = 0.0
            for (i in offset until offset + len) {
                sum += (buffer[i] * buffer[i]).toDouble()
            }
            return Math.sqrt(sum / len)
        }

        fun getDB(buffer: ShortArray, offset: Int, len: Int): Double {
            return getDB(getAmplitude(buffer, offset, len))
        }

        fun getDB(amplitude: Double): Double {
            // https://en.wikipedia.org/wiki/Sound_pressure
            return 20.0 * Math.log10(amplitude / 0x7FFF)
        }
    }
}