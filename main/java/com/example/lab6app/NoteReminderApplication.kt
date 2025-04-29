package com.example.lab6app

import android.app.Application
import com.example.lab6app.util.NotificationHelper
import com.google.android.material.color.DynamicColors

class NoteReminderApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        NotificationHelper.createNotificationChannel(this)
        NotificationHelper.createNotificationChannel(this)
    }
}