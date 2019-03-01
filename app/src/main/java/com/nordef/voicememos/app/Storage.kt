package com.nordef.voicememos.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Build
import android.os.StatFs
import android.preference.PreferenceManager
import androidx.core.content.ContextCompat
import com.nordef.voicememos.R
import org.apache.commons.io.comparator.LastModifiedFileComparator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class Storage(var context: Context) {

    val localStorage: File
        get() = File(context.applicationInfo.dataDir, RECORDINGS)

    val isLocalStorageEmpty: Boolean
        get() = localStorage.listFiles().size == 0

    val isExternalStoragePermitted: Boolean
        get() = permitted(PERMISSIONS)

    val storagePath: File
        get() {
            val shared = PreferenceManager.getDefaultSharedPreferences(context)
            val path = shared.getString(MainApplication.PREFERENCE_STORAGE, "")
            return if (permitted(PERMISSIONS)) {
                File(path)
            } else {
                localStorage
            }
        }

    val getNewFile: File
        get() {
            val s = SimpleDateFormat("${context.getString(R.string.date_format)} HH.mm.ss")

            val shared = PreferenceManager.getDefaultSharedPreferences(context)
            val ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "")

            val parent = storagePath
            if (!parent.exists()) {
                if (!parent.mkdirs())
                    throw RuntimeException("Unable to create: $parent")
            }

            return getNextFile(parent, s.format(Date()), ext)
        }

    // Starting in KITKAT, no permissions are required to read or write to the returned path;
    // it's always accessible to the calling app.
    val tempRecording: File
        get() {
            val internal = File(context.applicationInfo.dataDir, TMP_REC)

            if (internal.exists())
                return internal
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                if (!permitted(PERMISSIONS))
                    return internal
            }

            val external = File(context.externalCacheDir, TMP_REC)

            if (external.exists())
                return external

            val freeI = getFree(internal)
            val freeE = getFree(external)

            return if (freeI > freeE)
                internal
            else
                external
        }

    fun permitted(ss: Array<String>): Boolean {
        for (s in ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun recordingPending(): Boolean {
        return tempRecording.exists()
    }

    fun migrateLocalStorage() {
        if (!permitted(PERMISSIONS)) {
            return
        }

        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val path = shared.getString(MainApplication.PREFERENCE_STORAGE, "")

        val l = localStorage
        val t = File(path)

        t.mkdirs()

        val ff = l.listFiles() ?: return

        for (f in ff) {
            val tt = getNextFile(t, f)
            move(f, tt)
        }
    }

    internal fun getNextFile(parent: File, f: File): File {
        var fileName = f.name

        var extension = ""

        val i = fileName.lastIndexOf('.')
        if (i > 0) {
            extension = fileName.substring(i + 1)
            fileName = fileName.substring(0, i)
        }

        return getNextFile(parent, fileName, extension)
    }

    internal fun getNextFile(parent: File, name: String, ext: String?): File {
        var fileName: String
        if (ext!!.isEmpty())
            fileName = name
        else
            fileName = String.format("%s.%s", name, ext)

        var file = File(parent, fileName)

        var i = 1
        while (file.exists()) {
            fileName = String.format("%s (%d).%s", name, i, ext)
            file = File(parent, fileName)
            i++
        }

        return file
    }

    fun scan(dir: File): List<File> {
        val list = ArrayList<File>()

        val ff = dir.listFiles() ?: return list

        //sort files by date
        Arrays.sort(ff, LastModifiedFileComparator.LASTMODIFIED_COMPARATOR)
        Arrays.sort(ff, LastModifiedFileComparator.LASTMODIFIED_REVERSE)

        for (f in ff) {
            if (f.length() > 0) {
                val ee = context.resources.getStringArray(R.array.encodings_values)
                val n = f.name.toLowerCase()
                for (e in ee) {
                    if (n.endsWith(".$e"))
                        list.add(f)
                }
            }
        }

        return list
    }

    // get average recording miliseconds based on compression format
    fun average(free: Long): Long {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, "")!!)
        val ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "")

        if (ext == "m4a") {
            val y1: Long = 365723 // one minute sample 16000Hz
            val x1: Long = 16000 // at 16000
            val y2: Long = 493743 // one minute sample
            val x2: Long = 44000 // at 44000
            val x = rate.toLong()
            val y = (x - x1) * (y2 - y1) / (x2 - x1) + y1

            val m = if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2
            val perSec = y / 60 * m
            return free / perSec * 1000
        }

        // default raw
        val m = if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val c = if (RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 2 else 1
        val perSec = (c * m * rate).toLong()
        return free / perSec * 1000
    }

    fun getFree(f: File): Long {
        var f = f
        while (!f.exists())
            f = f.parentFile

        val fsi = StatFs(f.path)
        return if (Build.VERSION.SDK_INT < 18)
            (fsi.blockSize * fsi.availableBlocks).toLong()
        else
            fsi.blockSizeLong * fsi.availableBlocksLong
    }

    fun open(f: File): FileOutputStream {
        val parent = f.parentFile
        if (!parent.exists() && !parent.mkdirs()) {
            throw RuntimeException("unable to create: $parent")
        }
        if (!parent.isDirectory)
            throw RuntimeException("target is not a dir: $parent")
        try {
            return FileOutputStream(f, true)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    fun delete(f: File) {
        f.delete()
    }

    fun move(f: File, to: File) {
        try {
            val fis = FileInputStream(f)
            val out = FileOutputStream(to)

            val buf = ByteArray(1024)
            var len: Int = 0
            while ({ len = fis.read(buf); len }() > 0) {
                out.write(buf, 0, len)
            }
            fis.close()
            out.close()
            f.delete()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

    }

    companion object {
        val TMP_REC = "recorind.data"
        val RECORDINGS = "recordings"

        val PERMISSIONS = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun getNameNoExt(f: File): String {
            var fileName = f.name

            val i = fileName.lastIndexOf('.')
            if (i > 0) {
                fileName = fileName.substring(0, i)
            }
            return fileName
        }

        fun getExt(f: File): String {
            val fileName = f.name

            val i = fileName.lastIndexOf('.')
            return if (i > 0) {
                fileName.substring(i + 1)
            } else ""
        }
    }

}