package com.nordef.voicememos.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.preference.PreferenceManager


class Sound(internal var context: Context) {

    internal var soundMode: Int = 0

    fun silent() {
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !notificationManager.isNotificationPolicyAccessGranted
            ) {
                return
            }

            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            soundMode = am.ringerMode

            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                // we already in SILENT mode. keep all unchanged.
                soundMode = -1
                return
            }
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
        }
    }

    fun unsilent() {
        // keep unchanged
        if (soundMode == -1)
            return

        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {

            //silent mode for higher api
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && !notificationManager.isNotificationPolicyAccessGranted
            ) {
                return
            }

            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val soundMode = am.ringerMode
            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                am.ringerMode = this.soundMode
            }
        }
    }

    fun generateTrack(sampleRate: Int, buf: ShortArray, len: Int): AudioTrack {

        var c = 0

        if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO)
            c = AudioFormat.CHANNEL_OUT_MONO

        if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO)
            c = AudioFormat.CHANNEL_OUT_STEREO

        // old phones bug.
        // http://stackoverflow.com/questions/27602492
        //
        // with MODE_STATIC setNotificationMarkerPosition not called
        val track = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate,
                c, RawSamples.AUDIO_FORMAT,
                len * (java.lang.Short.SIZE / 8), AudioTrack.MODE_STREAM
        )
        track.write(buf, 0, len)
        if (track.setNotificationMarkerPosition(len) != AudioTrack.SUCCESS)
            throw RuntimeException("unable to set marker")
        return track
    }
}