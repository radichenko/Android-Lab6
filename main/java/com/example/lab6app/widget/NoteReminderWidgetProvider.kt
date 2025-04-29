package com.example.lab6app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.lab6app.R
import com.example.lab6app.activity.MainActivity
import com.example.lab6app.database.AppDatabase
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class NoteReminderWidgetProvider : AppWidgetProvider() {

    private val TAG = "NoteReminderApp"
    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val ACTION_UPDATE_WIDGET = "com.example.lab6app.action.UPDATE_WIDGET"

        /**
         * Метод для зручного відправлення broadcast-повідомлення на оновлення
         * з будь-якої частини програми.
         */
        fun sendUpdateBroadcast(context: Context) {
            Log.d("NoteReminderApp", "[WidgetProvider] Requesting widget update via broadcast.")
            val intent = Intent(context, NoteReminderWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            // Переконуємось, що інтент буде доставлено саме до нашого ресівера
            intent.component = ComponentName(context, NoteReminderWidgetProvider::class.java)
            context.sendBroadcast(intent)
            Log.d("NoteReminderApp", "[WidgetProvider] Update broadcast sent.")
        }
    }

    /**
     * Викликається системою для оновлення віджета за розкладом (updatePeriodMillis),
     * при першому додаванні віджета, а також нашим кастомним broadcast'ом.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "[WidgetProvider] onUpdate called for widget IDs: ${appWidgetIds.joinToString()}")
        // Оновлюємо кожен встановлений екземпляр віджета
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * Обробляє всі вхідні broadcast-повідомлення, включно зі стандартними
     * та нашим кастомним ACTION_UPDATE_WIDGET.
     */
    override fun onReceive(context: Context, intent: Intent) {
        // ВАЖЛИВО: Викликати суперклас для стандартної обробки (наприклад, onUpdate)
        super.onReceive(context, intent)

        Log.d(TAG, "[WidgetProvider] onReceive called with action: ${intent.action}")

        // Перевіряємо, чи це наш action для примусового оновлення
        if (ACTION_UPDATE_WIDGET == intent.action) {
            Log.d(TAG, "[WidgetProvider] Received force update broadcast.")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            // Отримуємо ID всіх встановлених віджетів нашого типу
            val componentName = ComponentName(context, NoteReminderWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            // Повідомляємо менеджеру, що дані змінились (особливо важливо для віджетів з колекціями)
            // Для простого віджету onUpdate зазвичай достатньо, але це не завадить
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_root_layout) // Використовуємо ID кореневого елемента або ID колекції
            // Явно викликаємо onUpdate для всіх віджетів
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }


    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        Log.d(TAG, "[WidgetProvider] Updating widget ID: $appWidgetId")

        // Запускаємо корутину для асинхронного доступу до БД
        widgetScope.launch {
            var noteText = context.getString(R.string.widget_loading)
            var timestampText = ""
            var clickIntentTargetNoteId: Int? = null // Для відкриття конкретної нотатки (поки не використовуємо)

            try {
                val noteDao = AppDatabase.getDatabase(context.applicationContext).noteDao()
                val currentTime = System.currentTimeMillis()
                val nextNote = noteDao.getNextUpcomingNote(currentTime) // Отримуємо наступну нотатку
                Log.d(TAG, "[WidgetProvider] Fetched next note for widget $appWidgetId: ${nextNote?.text ?: "None"}")

                if (nextNote != null) {
                    noteText = nextNote.text
                    timestampText = nextNote.timestamp?.let {
                        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: ""
                    clickIntentTargetNoteId = nextNote.id // Зберігаємо ID для потенційного кліку
                } else {
                    noteText = context.getString(R.string.widget_no_upcoming_notes)
                    timestampText = ""
                }

            } catch (e: Exception) {
                Log.e(TAG, "[WidgetProvider] Error accessing DB for widget ID $appWidgetId", e)
                noteText = context.getString(R.string.widget_error) // Повідомлення про помилку
            }

            // Створюємо RemoteViews - "пульт керування" для UI віджету
            val views = RemoteViews(context.packageName, R.layout.note_reminder_widget_layout)

            // Оновлюємо текстові поля
            views.setTextViewText(R.id.widget_note_text, noteText)
            views.setTextViewText(R.id.widget_note_timestamp, timestampText)

            // --- Налаштування кліку на віджет ---
            // Створюємо інтент, що відкриє MainActivity
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                // Додаємо прапори, щоб уникнути створення купи однакових Activity
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Можна додати екстра дані, якщо потрібно передати щось в MainActivity
                // putExtra("widget_id", appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId, // Використовуємо унікальний request code для кожного віджету
                mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Призначаємо PendingIntent на клік по кореневому елементу макету
            views.setOnClickPendingIntent(R.id.widget_root_layout, pendingIntent)
            Log.d(TAG, "[WidgetProvider] Click PendingIntent (to MainActivity) set for widget ID: $appWidgetId")
            // ------------------------------------

            // Оновлюємо віджет через AppWidgetManager (робимо це в головному потоці)
            try {
                withContext(Dispatchers.Main.immediate) { // immediate намагається виконати в поточному потоці, якщо це Main
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    Log.d(TAG, "[WidgetProvider] Widget UI update submitted for ID: $appWidgetId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[WidgetProvider] Error submitting widget update for ID $appWidgetId", e)
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        widgetScope.cancel()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }
}