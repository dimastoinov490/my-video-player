package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo

object OrientationHelper {
    const val PREFS_NAME = "video_prefs"
    const val KEY_MODE = "orientation_mode"
    const val KEY_LEGACY_AUTO_ROTATE = "auto_rotate"

    const val MODE_SYSTEM = "system"
    const val MODE_SENSOR = "sensor"
    const val MODE_PORTRAIT = "portrait"
    const val MODE_LANDSCAPE = "landscape"

    fun normalize(mode: String?): String = when (mode) {
        MODE_SYSTEM, MODE_SENSOR, MODE_PORTRAIT, MODE_LANDSCAPE -> mode
        else -> MODE_PORTRAIT
    }

    fun getMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MODE, null)
        return normalize(raw)
    }

    fun setMode(context: Context, mode: String): String {
        val norm = normalize(mode)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_MODE, norm)
            .putBoolean(KEY_LEGACY_AUTO_ROTATE, norm == MODE_SENSOR || norm == MODE_SYSTEM)
            .apply()
        return norm
    }

    fun toActivityOrientation(mode: String): Int = when (normalize(mode)) {
        MODE_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        MODE_SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        MODE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        MODE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun apply(activity: Activity, mode: String = getMode(activity)) {
        val target = toActivityOrientation(mode)
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }
}
