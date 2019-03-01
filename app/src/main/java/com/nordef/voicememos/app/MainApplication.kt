package com.nordef.voicememos.app

import android.app.Application
import android.content.Context
import android.os.Build
import android.preference.PreferenceManager
import com.nordef.voicememos.R
import com.nordef.voicememos.classes.ThemeUtils

class MainApplication : Application() {

    val userTheme: Int
        get() = getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark)

    override fun onCreate() {
        super.onCreate()

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false)

        val context = this
        context.setTheme(userTheme)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT_WATCH) {
            IS_LOWER_SDK = true
        }
    }

    fun formatFree(free: Long, left: Long): String {
        var str = ""

        val diffSeconds = (left / 1000 % 60).toInt()
        val diffMinutes = (left / (60 * 1000) % 60).toInt()
        val diffHours = (left / (60 * 60 * 1000) % 24).toInt()
        val diffDays = (left / (24 * 60 * 60 * 1000)).toInt()

        if (diffDays > 0) {
            str = resources.getQuantityString(R.plurals.days, diffDays, diffDays)
        } else if (diffHours > 0) {
            str = resources.getQuantityString(R.plurals.hours, diffHours, diffHours)
        } else if (diffMinutes > 0) {
            str = resources.getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes)
        } else if (diffSeconds > 0) {
            str = resources.getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds)
        }

        return getString(R.string.title_header, MainApplication.formatSize(this, free), str)
    }

    companion object {
        var IS_LOWER_SDK = false

        val PREFERENCE_STORAGE = "storage_path"
        val PREFERENCE_RATE = "sample_rate"
        val PREFERENCE_CALL = "call"
        val PREFERENCE_SILENT = "silence"
        val PREFERENCE_ENCODING = "encoding"
        val PREFERENCE_LAST = "last_recording"
        val PREFERENCE_THEME = "theme"

        fun getTheme(context: Context, light: Int, dark: Int): Int {
            val shared = PreferenceManager.getDefaultSharedPreferences(context)
            val theme = shared.getString(PREFERENCE_THEME, "")
            return if (theme == "Theme_Dark") {
                dark
            } else {
                light
            }
        }

        fun getActionbarColor(context: Context): Int {
            val colorId = MainApplication.getTheme(context, R.attr.colorPrimary, R.attr.secondBackground)
            return ThemeUtils.getThemeColor(context, colorId)
        }

        fun formatTime(tt: Int): String {
            return String.format("%02d", tt)
        }

        fun formatSize(context: Context, s: Long): String {
            if (s > 0.1 * 1024.0 * 1024.0 * 1024.0) {
                val f = s.toFloat() / 1024f / 1024f / 1024f
                return context.getString(R.string.size_gb, f)
            } else if (s > 0.1 * 1024.0 * 1024.0) {
                val f = s.toFloat() / 1024f / 1024f
                return context.getString(R.string.size_mb, f)
            } else {
                val f = s / 1024f
                return context.getString(R.string.size_kb, f)
            }
        }

        fun formatDuration(context: Context, diff: Long): String {
            val diffMilliseconds = (diff % 1000).toInt()
            val diffSeconds = (diff / 1000 % 60).toInt()
            val diffMinutes = (diff / (60 * 1000) % 60).toInt()
            val diffHours = (diff / (60 * 60 * 1000) % 24).toInt()
            val diffDays = (diff / (24 * 60 * 60 * 1000)).toInt()

            var str = ""

            if (diffDays > 0)
                str = diffDays.toString() + context.getString(R.string.days_symbol) + " " + formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds)
            else if (diffHours > 0)
                str = formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds)
            else
                str = formatTime(diffMinutes) + ":" + formatTime(diffSeconds)

            return str
        }
    }

}