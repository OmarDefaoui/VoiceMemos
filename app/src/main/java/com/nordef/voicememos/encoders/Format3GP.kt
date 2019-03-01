package com.nordef.voicememos.encoders

import android.annotation.TargetApi
import android.media.MediaFormat

import java.io.File

@TargetApi(21)
class Format3GP(info: EncoderInfo, out: File) : MuxerMP4() {

    init {
        val format = MediaFormat()

        // for low bitrate, AMR_NB
        run {
            format.setString(MediaFormat.KEY_MIME, "audio/3gpp")
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate) // 8000 only supported
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 12200) // set maximum
        }

        create(info, format, out)
    }
}
