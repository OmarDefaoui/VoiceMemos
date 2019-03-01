package com.nordef.voicememos.encoders

interface Encoder {
    fun encode(buf: ShortArray)

    fun close()
}
