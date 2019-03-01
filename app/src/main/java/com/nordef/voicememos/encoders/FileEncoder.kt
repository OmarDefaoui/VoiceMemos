package com.nordef.voicememos.encoders

import android.content.Context
import android.os.Handler
import android.util.Log

import com.nordef.voicememos.app.RawSamples

import java.io.File

class FileEncoder(internal var context: Context, internal var `in`: File, internal var encoder: Encoder) {

    internal var handler: Handler
    internal var thread: Thread? = null
    internal var samples: Long = 0
    internal var cur: Long = 0
    lateinit var exception: Throwable
        internal set

    val progress: Int
        get() = synchronized(thread!!) {
            return (cur * 100 / samples).toInt()
        }

    init {

        handler = Handler()
    }

    fun run(progress: Runnable, done: Runnable, error: Runnable) {
        thread = Thread(Runnable {
            cur = 0

            val rs = RawSamples(`in`)

            samples = rs.samples

            val buf = ShortArray(1000)

            rs.open(buf.size)

            try {
                while (!Thread.currentThread().isInterrupted) {
                    val len = rs.read(buf).toLong()
                    if (len <= 0) {
                        handler.post(done)
                        return@Runnable
                    } else {
                        encoder.encode(buf)
                        handler.post(progress)
                        synchronized(thread!!) {
                            cur += len
                        }
                    }
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Exception", e)
                exception = e
                handler.post(error)
            } finally {
                encoder.close()
                rs?.close()
            }
        })
        thread!!.start()
    }

    fun close() {
        if (thread != null) {
            thread!!.interrupt()
            thread = null
        }
    }

    companion object {
        val TAG = FileEncoder::class.java.simpleName
    }
}
