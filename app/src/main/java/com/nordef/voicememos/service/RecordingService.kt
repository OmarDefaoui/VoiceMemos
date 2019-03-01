package com.nordef.voicememos.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.nordef.voicememos.MainActivity
import com.nordef.voicememos.R
import com.nordef.voicememos.app.MainApplication
import com.nordef.voicememos.app.MainApplication.Companion.IS_LOWER_SDK

class RecordingService : Service() {

    internal lateinit var receiver: RecordingReceiver

    internal lateinit var targetFile: String
    internal var recording: Boolean = false

    val is_low_sdk = IS_LOWER_SDK

    inner class RecordingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                // showRecordingActivity();
            }
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        receiver = RecordingReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(receiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        if (intent != null) {
            val a = intent.action

            if (a == null) {
                targetFile = intent.getStringExtra("targetFile")
                recording = intent.getBooleanExtra("recording", false)
                showNotificationAlarm(true)
            } else if (a == PAUSE_BUTTON) {
                val i = Intent(MainActivity.PAUSE_BUTTON)
                sendBroadcast(i)
            } else if (a == SHOW_ACTIVITY) {
                //MainActivity.startActivity(this, false)
                MainActivity.startActivity(this)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    inner class Binder : android.os.Binder() {
        val service: RecordingService
            get() = this@RecordingService
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestory")

        showNotificationAlarm(false)

        unregisterReceiver(receiver)
    }

    // alarm dismiss button
    fun showNotificationAlarm(show: Boolean) {
        if (is_low_sdk)
            showNotificationAlarmLowSDK(show)
        else
            showNotificationAlarmHighSDK(show)
    }

    fun showNotificationAlarmLowSDK(show: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!show) {
            notificationManager.cancel(NOTIFICATION_RECORDING_ICON)
        } else {
            val main = PendingIntent.getService(this, 0,
                    Intent(this, RecordingService::class.java).setAction(SHOW_ACTIVITY),
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val pe = PendingIntent.getService(this, 0,
                    Intent(this, RecordingService::class.java).setAction(PAUSE_BUTTON),
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val view = RemoteViews(packageName,
                    MainApplication.getTheme(baseContext,
                            R.layout.notifictaion_recording_light,
                            R.layout.notifictaion_recording_dark))

            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main)
            view.setTextViewText(R.id.notification_text, ".../$targetFile")
            view.setOnClickPendingIntent(R.id.notification_pause, pe)
            view.setImageViewResource(R.id.notification_pause, if (!recording) R.drawable.ic_play_png
            else R.drawable.ic_pause_png)
            view.setImageViewResource(R.id.iv_main, R.drawable.ic_big_mic_png)

            val builder = NotificationCompat.Builder(this,
                    getString(R.string.recording_notification_id))
                    .setOngoing(true)
                    .setVibrate(longArrayOf(0))
                    .setContentTitle(getString(R.string.recording_title))
                    .setSmallIcon(R.drawable.ic_mic_png)
                    .setContent(view)

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build())
        }
    }

    fun showNotificationAlarmHighSDK(show: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!show) {
            notificationManager.cancel(NOTIFICATION_RECORDING_ICON)
        } else {
            val main = PendingIntent.getService(this, 0,
                    Intent(this, RecordingService::class.java).setAction(SHOW_ACTIVITY),
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val pe = PendingIntent.getService(this, 0,
                    Intent(this, RecordingService::class.java).setAction(PAUSE_BUTTON),
                    PendingIntent.FLAG_UPDATE_CURRENT)

            val view = RemoteViews(packageName,
                    MainApplication.getTheme(baseContext,
                            R.layout.notifictaion_recording_light,
                            R.layout.notifictaion_recording_dark))

            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main)
            view.setTextViewText(R.id.notification_text, ".../$targetFile")
            view.setOnClickPendingIntent(R.id.notification_pause, pe)
            view.setImageViewResource(R.id.notification_pause, if (!recording) R.drawable.ic_play
            else R.drawable.ic_pause)
            view.setImageViewResource(R.id.iv_main, R.drawable.ic_mic)

            val builder = NotificationCompat.Builder(this,
                    getString(R.string.recording_notification_id))
                    .setOngoing(true)
                    .setVibrate(longArrayOf(0))
                    .setContentTitle(getString(R.string.recording_title))
                    .setSmallIcon(R.drawable.ic_mic)
                    .setContent(view)

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build())
        }
    }

    companion object {
        val TAG = RecordingService::class.java.simpleName

        val NOTIFICATION_RECORDING_ICON = 1

        var SHOW_ACTIVITY = RecordingService::class.java.canonicalName!! + ".SHOW_ACTIVITY"
        var PAUSE_BUTTON = RecordingService::class.java.canonicalName!! + ".PAUSE_BUTTON"

        fun startService(context: Context, targetFile: String, recording: Boolean) {
            context.startService(Intent(context, RecordingService::class.java)
                    .putExtra("targetFile", targetFile)
                    .putExtra("recording", recording))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}

