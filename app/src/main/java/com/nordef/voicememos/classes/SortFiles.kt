package com.nordef.voicememos.classes

import java.io.File
import java.util.*

class SortFiles : Comparator<File> {
    override fun compare(file: File, file2: File): Int {
        /*return if (file.isDirectory && file2.isFile)
            -1
        else if (file.isFile && file2.isDirectory)
            1
        else
            file.path.compareTo(file2.path)*/
        return (if (file.lastModified() > file2.lastModified())
            file.lastModified() else file2.lastModified()).toInt()
    }
}