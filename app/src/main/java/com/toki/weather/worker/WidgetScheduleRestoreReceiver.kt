package com.toki.weather.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores periodic work for widgets already installed when the APK is replaced. */
class WidgetScheduleRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WeatherWorkScheduler.onWidgetUpdated(context, goAsync())
        }
    }
}
