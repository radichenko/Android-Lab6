package com.example.lab6app.util

import android.Manifest // Додано
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager // Додано
import android.os.Build
import android.util.Log // Додано
import androidx.core.app.ActivityCompat // Додано
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lab6app.R
import com.example.lab6app.activity.AddEditNoteActivity // Перевірте шлях

object NotificationHelper {

    private const val CHANNEL_ID = "note_reminder_channel_1" // Змініть ID, якщо треба скинути налаштування каналу
    private const val CHANNEL_NAME = "Нагадування Нотаток"
    private const val CHANNEL_DESCRIPTION = "Канал для сповіщень-нагадувань про нотатки"
    private const val TAG = "NoteReminderApp" // Тег для логів

    fun createNotificationChannel(context: Context) {
        // Створюємо канал тільки на Android 8.0 (API 26) і вище
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH // Високий пріоритет, щоб сповіщення з'являлось зверху
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true) // Увімкнути вібрацію за замовчуванням для каналу
                // Можна налаштувати звук, світло тощо:
                // setSound(defaultSoundUri, audioAttributes)
                // lightColor = Color.RED
                // enableLights(true)
            }
            // Реєструємо канал в системі
            val notificationManager: NotificationManager? =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "[NotificationHelper] Notification channel '$CHANNEL_ID' created or already exists.")
            } else {
                Log.e(TAG, "[NotificationHelper] NotificationManager is null, cannot create channel.")
            }
        } else {
            Log.d(TAG, "[NotificationHelper] Notification channel not needed (API < 26).")
        }
    }

    fun showNotification(context: Context, noteId: Int, noteText: String) {
        Log.d(TAG, "[NotificationHelper] Attempting to show notification for note ID: $noteId, text: '$noteText'")

        // Перевірка дозволу перед створенням сповіщення (на API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        {
            Log.e(TAG, "[NotificationHelper] POST_NOTIFICATIONS permission missing. Cannot show notification for note ID: $noteId")
            // У ресівері ми не можемо запитати дозвіл.
            // Просто не показуємо сповіщення. Дозвіл мав бути наданий раніше.
            return
        }


        // Інтент для відкриття Activity при натисканні
        val intent = Intent(context, AddEditNoteActivity::class.java).apply {
            // Важливо очистити стек і створити новий таск,
            // щоб уникнути дивної навігації при відкритті з сповіщення
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AddEditNoteActivity.EXTRA_NOTE_ID, noteId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            noteId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Оновлюємо дані, якщо вже існує
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // **ПЕРЕКОНАЙТЕСЬ, ЩО ІКОНКА ІСНУЄ!**
            .setContentTitle(context.getString(R.string.notification_title)) // Використовуємо рядок з ресурсів
            .setContentText(noteText) // Основний текст нотатки
            // .setStyle(NotificationCompat.BigTextStyle().bigText(noteText)) // Для довгого тексту
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Пріоритет (важливо для Heads-Up сповіщень)
            .setContentIntent(pendingIntent) // Дія при натисканні
            .setAutoCancel(true) // Закривати сповіщення після натискання
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Використовувати звук/вібрацію/світло за замовчуванням
        try {
            NotificationManagerCompat.from(context).notify(noteId, builder.build()) // Використовуємо ID нотатки як ID сповіщення
            Log.d(TAG, "[NotificationHelper] Notification successfully posted for note ID: $noteId")
        } catch (e: SecurityException) {
            // Ця помилка не мала б виникати, якщо ми перевірили дозвіл вище, але про всяк випадок
            Log.e(TAG, "[NotificationHelper] SecurityException while posting notification for note ID: $noteId", e)
        } catch (e: Exception) {
            Log.e(TAG, "[NotificationHelper] Exception while posting notification for note ID: $noteId", e)
        }
    }
}