package com.example.lab6app.receivers



import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lab6app.database.AppDatabase
import com.example.lab6app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTE_ID = "com.example.lab6app.EXTRA_NOTE_ID"
        // Додамо константу для action
        const val ACTION_ALARM = "com.example.lab6app.ALARM_TRIGGER."
        private const val TAG = "NoteReminderApp" // Тег для логів
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job) // Використовуємо IO для доступу до БД

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "[AlarmReceiver] onReceive triggered!")

        if (context == null || intent == null) {
            Log.w(TAG, "[AlarmReceiver] Context or Intent is null")
            return
        }
        Log.d(TAG, "[AlarmReceiver] Received action: ${intent.action}") // Логуємо action

        // Перевіряємо, чи action відповідає нашому шаблону (опціонально, але корисно)
        if (intent.action?.startsWith(ACTION_ALARM) != true) {
            Log.w(TAG, "[AlarmReceiver] Received unknown action: ${intent.action}")
            // return // Розкоментуйте, якщо хочете ігнорувати невідомі action
        }


        val noteId = intent.getIntExtra(EXTRA_NOTE_ID, -1)
        Log.d(TAG, "[AlarmReceiver] Received note ID: $noteId")

        if (noteId == -1) {
            Log.e(TAG, "[AlarmReceiver] Invalid Note ID received (-1), aborting.")
            return
        }

        val pendingResult: PendingResult = goAsync() // Важливо для асинхронної роботи

        scope.launch {
            try {
                Log.d(TAG, "[AlarmReceiver] Coroutine started for note ID: $noteId. Accessing DB...")
                // Отримуємо DAO безпечно
                val database = AppDatabase.getDatabase(context)
                val noteDao = database.noteDao()
                val note = noteDao.getNoteById(noteId)
                Log.d(TAG, "[AlarmReceiver] Note fetched from DB for ID $noteId: ${note != null}")

                if (note != null) {
                    // Перевіряємо, чи час нотатки все ще актуальний (опціонально)
                    // Можливо, користувач змінив час після планування, але до спрацювання
                    // Тут можна додати перевірку note.timestamp
                    Log.d(TAG, "[AlarmReceiver] Note found: '${note.text}'. Calling NotificationHelper...")
                    // Канал створюється в Application, тут можна не викликати
                    // NotificationHelper.createNotificationChannel(context)
                    NotificationHelper.showNotification(context, note.id, note.text)
                    Log.d(TAG,"[AlarmReceiver] NotificationHelper.showNotification called for note ID: $noteId")
                } else {
                    Log.w(TAG,"[AlarmReceiver] Note with ID $noteId not found in database. Maybe deleted?")
                    // Можливо, варто скасувати будильник тут, якщо нотатки немає?
                    // AlarmScheduler.cancelAlarm(context, noteId)
                }
            } catch (e: Exception) {
                Log.e(TAG,"[AlarmReceiver] Error processing alarm for note ID: $noteId", e)
            } finally {
                // ВАЖЛИВО: Завершити обробку, щоб система знала, що ми закінчили
                pendingResult.finish()
                Log.d(TAG,"[AlarmReceiver] Coroutine finished processing alarm for note ID: $noteId")
            }
        }
    }
}