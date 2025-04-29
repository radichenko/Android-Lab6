package com.example.lab6app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.lab6app.model.Note
import com.example.lab6app.receivers.AlarmReceiver

object AlarmScheduler {

    private const val TAG = "NoteReminderApp" // Тег для логів

    fun scheduleAlarm(context: Context, note: Note) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        Log.d(TAG, "[Scheduler] scheduleAlarm called for note ID: ${note.id}, time: ${note.timestamp}")

        // Перевірка дозволу на точні будильники (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "[Scheduler] Cannot schedule exact alarms for note ID: ${note.id}. Need permission.")
            // Не плануємо, якщо немає дозволу. Повідомлення користувачу має бути в Activity.
            return
        }

        note.timestamp?.let { timeInMillis ->
            if (timeInMillis > System.currentTimeMillis()) {
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra(AlarmReceiver.EXTRA_NOTE_ID, note.id)
                    // Важливо: Action або data роблять інтент унікальним для PendingIntent
                    action = AlarmReceiver.ACTION_ALARM + note.id // Використовуємо action з ID
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    note.id, // Request code = note ID
                    intent,
                    // FLAG_IMMUTABLE є обов'язковим з Android 12 (API 31)
                    // FLAG_UPDATE_CURRENT оновлює екстра дані, якщо PendingIntent вже існує
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    Log.d(TAG, "[Scheduler] Setting alarm for note ID: ${note.id} with PendingIntent requestCode: ${note.id}, action: ${intent.action}")
                    // Використовуємо setExactAndAllowWhileIdle для точності
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeInMillis,
                        pendingIntent
                    )
                    Log.d(TAG, "[Scheduler] Alarm successfully set for note ID: ${note.id} at $timeInMillis")
                } catch (se: SecurityException) {
                    // Може виникнути, якщо дозвіл раптом зник
                    Log.e(TAG, "[Scheduler] SecurityException while scheduling alarm for note ${note.id}", se)
                } catch (e: Exception) {
                    Log.e(TAG, "[Scheduler] Exception while scheduling alarm for note ${note.id}", e)
                }

            } else {
                Log.w(TAG, "[Scheduler] Timestamp for note ${note.id} ($timeInMillis) is in the past. Alarm not set.")
            }
        } ?: Log.w(TAG, "[Scheduler] Timestamp for note ${note.id} is null. Alarm not set.")
    }

    fun cancelAlarm(context: Context, noteId: Int) {
        Log.d(TAG, "[Scheduler] cancelAlarm called for note ID: $noteId")
        if (noteId <= 0) {
            Log.w(TAG, "[Scheduler] Invalid note ID ($noteId) for cancelAlarm.")
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_NOTE_ID, noteId)
            action = AlarmReceiver.ACTION_ALARM + noteId
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Також скасовуємо сам PendingIntent
            Log.d(TAG, "[Scheduler] Alarm cancelled successfully for note ID: $noteId")
        } else {
            Log.w(TAG, "[Scheduler] PendingIntent not found for cancelling alarm for note ID: $noteId. Maybe it wasn't set or already triggered?")
        }
    }
}