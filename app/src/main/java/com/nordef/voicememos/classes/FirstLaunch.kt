package com.nordef.voicememos.classes

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.nordef.voicememos.MainActivity
import com.nordef.voicememos.R
import com.nordef.voicememos.on_bording_screen.OnBoardingScreenActivity


class FirstLaunch(val context: Context) {

    fun checkFirstLaunch() {
        //init sharedpreferences
        val sharedPref = context.getSharedPreferences("info", Context.MODE_PRIVATE)

        //do this only on the first launch time
        if (!sharedPref.getBoolean("haveData", false)) {

            setUpNotificationChannel()
            (context as MainActivity).finish()
            context.startActivity(Intent(context, OnBoardingScreenActivity::class.java))
        }
    }

    fun setUpNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel1 = NotificationChannel(
                context.getString(R.string.recording_notification_id),
                context.getString(R.string.recording_notification_name),
                NotificationManager.IMPORTANCE_LOW
            );
            channel1.description = context.getString(R.string.recording_notification_description);
            channel1.enableLights(false);
            channel1.enableVibration(false);

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel1)
        }
    }

}