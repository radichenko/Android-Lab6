package com.example.lab6app.viewmodel // Або ваш пакет

import android.app.Application
import android.util.Log // Імпорт логування
import androidx.lifecycle.*
import com.example.lab6app.database.AppDatabase
import com.example.lab6app.model.Note
import com.example.lab6app.util.AlarmScheduler
import com.example.lab6app.widget.NoteReminderWidgetProvider // Імпорт віджет провайдера
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Імпорт для withContext
import com.example.lab6app.database.NoteDao

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao: NoteDao
    val allNotes: LiveData<List<Note>>
    private val TAG = "NoteReminderApp" // Тег для логів

    init {
        Log.d(TAG, "[ViewModel] Initializing...")
        val database = AppDatabase.getDatabase(application)
        noteDao = database.noteDao()
        allNotes = noteDao.getAllNotes()
        Log.d(TAG, "[ViewModel] Initialized.")
    }

    /**
     * Вставляє нотатку в БД і повертає її новий ID через LiveData.
     * Також ініціює оновлення віджету.
     */
    fun insertNote(note: Note): LiveData<Long> {
        val insertedIdLiveData = MutableLiveData<Long>()
        viewModelScope.launch(Dispatchers.IO) {
            var newId: Long = -1 // Ініціалізуємо ID
            try {
                Log.d(TAG, "[ViewModel] Inserting note: ${note.text}")
                newId = noteDao.insert(note) // Отримуємо ID
                Log.d(TAG, "[ViewModel] Note inserted with ID: $newId")
            } catch (e: Exception) {
                Log.e(TAG, "[ViewModel] Error inserting note into DB", e)
                newId = -1 // Вказуємо на помилку
            } finally {
                insertedIdLiveData.postValue(newId) // Повідомляємо ID (або -1 при помилці)
                // Навіть якщо була помилка вставки, ID все одно треба повідомити
                // Але оновлюємо віджет тільки при успішній вставці
                if (newId > 0) {
                    // Оновлюємо віджет після вставки
                    triggerWidgetUpdate("insertNote")
                }
            }
        }
        return insertedIdLiveData
    }

    /**
     * Планує будильник для нотатки з вказаним ID та часом.
     * Викликається з Activity ПІСЛЯ отримання дійсного ID.
     */
    fun scheduleNoteAlarm(noteId: Long, timestamp: Long?) {
        if (noteId <= 0) {
            Log.e(TAG, "[ViewModel] Cannot schedule alarm, invalid note ID: $noteId")
            return
        }
        timestamp?.let { ts ->
            if (ts > System.currentTimeMillis()) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val note = noteDao.getNoteById(noteId.toInt()) // Отримуємо повну нотатку за ID
                        if (note != null) {
                            Log.d(TAG, "[ViewModel] Attempting to schedule alarm for note ID: ${note.id} at $ts")
                            AlarmScheduler.scheduleAlarm(getApplication(), note) // Передаємо об'єкт Note
                        } else {
                            Log.e(TAG, "[ViewModel] Note not found for scheduling, ID: $noteId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ViewModel] Error scheduling alarm for note ID $noteId", e)
                    }
                }
            } else {
                Log.w(TAG, "[ViewModel] Timestamp for note ID $noteId is in the past. Alarm not scheduled.")
            }
        }
    }

    /**
     * Оновлює нотатку в БД, переплановує/скасовує будильник та ініціює оновлення віджету.
     */
    fun updateAndSchedule(note: Note, oldTimestamp: Long?) = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "[ViewModel] Updating note ID: ${note.id}, Text: ${note.text}")
            // Скасувати старий будильник, якщо він був
            oldTimestamp?.let {
                if (note.id != 0) {
                    Log.d(TAG, "[ViewModel] Attempting to cancel old alarm for updated note ID: ${note.id}")
                    AlarmScheduler.cancelAlarm(getApplication(), note.id)
                }
            }

            noteDao.update(note)
            Log.d(TAG, "[ViewModel] Note updated in DB for ID: ${note.id}")

            // Оновлюємо віджет після оновлення
            triggerWidgetUpdate("updateAndSchedule")

            // Запланувати новий будильник, якщо потрібно
            if (note.timestamp != null && note.timestamp > System.currentTimeMillis()) {
                Log.d(TAG, "[ViewModel] Attempting to schedule new alarm for updated note ID: ${note.id} at ${note.timestamp}")
                AlarmScheduler.scheduleAlarm(getApplication(), note)
            } else {
                Log.d(TAG, "[ViewModel] New timestamp for note ID ${note.id} is in the past or null. Alarm not scheduled.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ViewModel] Error updating note ID ${note.id}", e)
        }
    }

    /**
     * Видаляє нотатку з БД, скасовує будильник та ініціює оновлення віджету.
     */
    fun deleteAndCancel(note: Note) = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "[ViewModel] Deleting note ID: ${note.id}, Text: ${note.text}")
            noteDao.delete(note)
            // Скасувати будильник для цієї нотатки
            if (note.id != 0) {
                Log.d(TAG, "[ViewModel] Attempting to cancel alarm for deleted note ID: ${note.id}")
                AlarmScheduler.cancelAlarm(getApplication(), note.id)
            }
            Log.d(TAG, "[ViewModel] Note deleted from DB for ID: ${note.id}")

            // Оновлюємо віджет після видалення
            triggerWidgetUpdate("deleteAndCancel")

        } catch (e: Exception) {
            Log.e(TAG, "[ViewModel] Error deleting note ID ${note.id}", e)
        }
    }

    /**
     * Відновлює нотатку для дії "Скасувати" (Undo).
     * УВАГА: Проста вставка може призвести до зміни ID. Розгляньте update або isDeleted.
     * Ініціює оновлення віджету.
     */
    fun insertAndScheduleForUndo(note: Note) = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "[ViewModel] Re-inserting note for UNDO, ID: ${note.id}, Text: ${note.text}")
            // ВАЖЛИВО: Простий insert може змінити ID, що зламає логіку, якщо ID використовується далі.
            // Краще мати поле isDeleted або використовувати update. Поки що залишаємо insert для простоти.
            val potentiallyNewId = noteDao.insert(note)
            Log.w(TAG, "[ViewModel] Re-inserted note for UNDO. Original ID: ${note.id}, Insert returned ID: $potentiallyNewId")

            // Оновлюємо віджет після відновлення
            triggerWidgetUpdate("insertAndScheduleForUndo")

            // Переплануємо будильник після відновлення, використовуючи ОРИГІНАЛЬНИЙ ID, якщо він є
            if (note.timestamp != null && note.timestamp > System.currentTimeMillis() && note.id != 0) {
                Log.d(TAG, "[ViewModel] Attempting to re-schedule alarm for UNDO note ID: ${note.id} at ${note.timestamp}")
                // Спробуємо отримати нотатку з БД з ОРИГІНАЛЬНИМ ID, хоча вона могла бути перезаписана
                val restoredNote = noteDao.getNoteById(note.id)
                if (restoredNote != null) {
                    AlarmScheduler.scheduleAlarm(getApplication(), restoredNote)
                } else {
                    Log.e(TAG, "[ViewModel] Cannot re-schedule alarm for UNDO, note with original ID ${note.id} not found after re-insert.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ViewModel] Error re-inserting note for UNDO, ID ${note.id}", e)
        }
    }

    /**
     * Допоміжна функція для виклику оновлення віджету з головного потоку.
     */
    private suspend fun triggerWidgetUpdate(callerMethod: String) {
        try {
            withContext(Dispatchers.Main) {
                Log.d(TAG, "[ViewModel] Requesting widget update from $callerMethod.")
                NoteReminderWidgetProvider.sendUpdateBroadcast(getApplication())
            }
        } catch (e: Exception) {
            Log.e(TAG, "[ViewModel] Error sending widget update broadcast from $callerMethod", e)
        }
    }


    fun getNoteById(id: Int): LiveData<Note?> {
        val result = MutableLiveData<Note?>()
        viewModelScope.launch(Dispatchers.IO) {
            result.postValue(noteDao.getNoteById(id))
        }
        return result
    }
}