package com.example.lab6app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lab6app.database.AppDatabase
import com.example.lab6app.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val TAG = "NoteReminderApp" // Тег для логів

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "[BootReceiver] onReceive triggered.")
        if (context != null && intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "[BootReceiver] Boot completed event received.")
            val pendingResult = goAsync() // Важливо для асинхронної роботи
            scope.launch {
                var rescheduledCount = 0
                try {
                    Log.d(TAG, "[BootReceiver] Coroutine started. Accessing DB...")
                    val noteDao = AppDatabase.getDatabase(context).noteDao()
                    // Використовуємо новий suspend метод
                    val notes = noteDao.getAllNotesList()
                    val currentTime = System.currentTimeMillis()
                    Log.d(TAG, "[BootReceiver] Found ${notes.size} notes in DB. Rescheduling future alarms.")

                    notes.forEach { note ->
                        note.timestamp?.let { timestamp ->
                            if (timestamp > currentTime) {
                                Log.d(TAG, "[BootReceiver] Rescheduling alarm for note ID: ${note.id} at $timestamp")
                                AlarmScheduler.scheduleAlarm(context, note)
                                rescheduledCount++
                            } else {
                                Log.d(TAG, "[BootReceiver] Skipping note ID: ${note.id}, timestamp is in the past.")
                            }
                        } ?: Log.d(TAG, "[BootReceiver] Skipping note ID: ${note.id}, timestamp is null.")
                    }
                    Log.d(TAG, "[BootReceiver] Finished iterating notes. Rescheduled $rescheduledCount alarms.")
                } catch (e: Exception) {
                    Log.e(TAG, "[BootReceiver] Error rescheduling alarms on boot", e)
                } finally {
                    pendingResult.finish()
                    Log.d(TAG, "[BootReceiver] Coroutine finished.")
                }
            }
        } else {
            Log.w(TAG, "[BootReceiver] Received intent with null context or invalid action: ${intent?.action}")
        }
    }
}