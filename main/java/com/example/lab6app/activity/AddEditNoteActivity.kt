package com.example.lab6app.activity


import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context // Додано
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log // Додано
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog // Додано для пояснень
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lab6app.R
import com.example.lab6app.model.Note
import com.example.lab6app.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.*

class AddEditNoteActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "com.example.lab6app.EXTRA_NOTE_ID"
        private const val INVALID_NOTE_ID = -1
        private const val TAG = "NoteReminderApp" // Тег для логів
    }

    private lateinit var editTextNoteText: EditText
    private lateinit var textViewSelectedDate: TextView
    private lateinit var buttonPickDate: Button
    private lateinit var textViewSelectedTime: TextView
    private lateinit var buttonPickTime: Button
    private lateinit var buttonSaveNote: Button

    private val noteViewModel: NoteViewModel by viewModels()
    private var currentNoteId: Int = INVALID_NOTE_ID
    private var currentNote: Note? = null
    private var oldTimestamp: Long? = null
    private val calendar = Calendar.getInstance()

    // --- Лаунчери для дозволів ---
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "[AddEdit] Notification permission GRANTED.")
                checkExactAlarmPermissionAndSave() // Переходимо до наступної перевірки
            } else {
                Log.w(TAG, "[AddEdit] Notification permission DENIED.")
                // Пояснити та/або зберегти без нагадування
                showPermissionDeniedDialog(
                    "Дозвіл на сповіщення відхилено",
                    "Без цього дозволу ви не отримуватимете нагадування. Ви можете надати дозвіл у налаштуваннях програми."
                ) {
                    saveNoteInternal(scheduleAlarm = false) // Зберегти без планування
                }
            }
        }

    private val requestExactAlarmSettingLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Користувач повернувся з налаштувань
            Log.d(TAG, "[AddEdit] Returned from Exact Alarm settings.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if(alarmManager.canScheduleExactAlarms()) {
                    Log.d(TAG, "[AddEdit] Exact alarm permission GRANTED via settings.")
                    saveNoteInternal() // Дозвіл надано, зберігаємо з плануванням
                } else {
                    Log.w(TAG, "[AddEdit] Exact alarm permission DENIED after settings.")
                    showPermissionDeniedDialog(
                        "Дозвіл на точні будильники відхилено",
                        "Нагадування можуть спрацьовувати невчасно або зовсім не спрацьовувати без цього дозволу."
                    ) {
                        saveNoteInternal(scheduleAlarm = false) // Зберегти без планування
                    }
                }
            } else {
                saveNoteInternal() // На версіях < S цей шлях не мав би виконатись
            }
        }
    // --- Кінець лаунчерів ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_note)
        Log.d(TAG, "[AddEdit] onCreate called.")

        editTextNoteText = findViewById(R.id.editTextNoteText)
        textViewSelectedDate = findViewById(R.id.textViewSelectedDate)
        buttonPickDate = findViewById(R.id.buttonPickDate)
        textViewSelectedTime = findViewById(R.id.textViewSelectedTime)
        buttonPickTime = findViewById(R.id.buttonPickTime)
        buttonSaveNote = findViewById(R.id.buttonSaveNote)

        currentNoteId = intent.getIntExtra(EXTRA_NOTE_ID, INVALID_NOTE_ID)
        Log.d(TAG, "[AddEdit] Received note ID: $currentNoteId")

        if (currentNoteId != INVALID_NOTE_ID) {
            title = getString(R.string.title_edit_note)
            noteViewModel.getNoteById(currentNoteId).observe(this) { note ->
                note?.let {
                    Log.d(TAG, "[AddEdit] Editing existing note: ${it.text}")
                    currentNote = it
                    editTextNoteText.setText(it.text)
                    it.timestamp?.let { ts ->
                        calendar.timeInMillis = ts
                        oldTimestamp = ts
                        updateDateAndTimeViews()
                    } ?: run {
                        // Якщо час null, можливо, встановити поточний або залишити як є
                        Log.d(TAG, "[AddEdit] Existing note has null timestamp.")
                        // calendar.timeInMillis = System.currentTimeMillis() // Розкоментуйте, якщо треба встановити поточний
                        updateDateAndTimeViews() // Оновити вигляд без дати/часу
                    }
                } ?: run {
                    Log.e(TAG, "[AddEdit] Note with ID $currentNoteId not found in DB!")
                    Toast.makeText(this, R.string.error_note_not_found, Toast.LENGTH_SHORT).show()
                    finish() // Закриваємо, якщо нотатку не знайдено
                }
            }
        } else {
            title = getString(R.string.title_add_note)
            // Встановлюємо час за замовчуванням (наприклад, +1 година)
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            Log.d(TAG, "[AddEdit] Creating new note. Default time set to: ${calendar.time}")
            updateDateAndTimeViews()
        }

        buttonPickDate.setOnClickListener { showDatePicker() }
        buttonPickTime.setOnClickListener { showTimePicker() }
        buttonSaveNote.setOnClickListener { checkPermissionsAndSave() }
    }

    // --- Логіка збереження та перевірки дозволів ---
    private fun checkPermissionsAndSave() {
        Log.d(TAG, "[AddEdit] checkPermissionsAndSave called.")
        val noteText = editTextNoteText.text.toString().trim()
        val timestamp = calendar.timeInMillis

        if (noteText.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_text), Toast.LENGTH_SHORT).show()
            return
        }

        val shouldSchedule = timestamp > System.currentTimeMillis()
        if (!shouldSchedule) {
            Log.d(TAG, "[AddEdit] Timestamp ($timestamp) is in the past or null, saving without scheduling.")
            saveNoteInternal(scheduleAlarm = false)
            return
        }

        // 1. Перевірка дозволу на СПОВІЩЕННЯ (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "[AddEdit] Notification permission already granted.")
                    checkExactAlarmPermissionAndSave() // Перехід до наступної перевірки
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.i(TAG, "[AddEdit] Showing rationale for notification permission.")
                    showPermissionRationaleDialog(
                        "Потрібен дозвіл на сповіщення",
                        "Цей дозвіл необхідний для показу нагадувань про ваші нотатки у вказаний час."
                    ) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                else -> {
                    Log.d(TAG, "[AddEdit] Requesting notification permission for the first time.")
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // Результат буде оброблено в колбеку лаунчера
                }
            }
        } else {
            // Версія < Android 13, дозвіл не потрібен
            Log.d(TAG, "[AddEdit] Notification permission not required (API < 33).")
            checkExactAlarmPermissionAndSave()
        }
    }

    private fun checkExactAlarmPermissionAndSave() {
        Log.d(TAG, "[AddEdit] checkExactAlarmPermissionAndSave called.")
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "[AddEdit] Exact alarm permission NOT granted. Requesting settings...")
            showPermissionRationaleDialog(
                "Потрібен дозвіл на точні будильники",
                "Щоб нагадування спрацьовували точно у вказаний час, потрібен спеціальний дозвіл. Будь ласка, увімкніть його для нашого додатку в наступному вікні налаштувань."
            ) {
                try {
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).also {
                        // Додаємо пакет, щоб перенаправити на налаштування САМЕ НАШОЇ програми
                        it.data = Uri.parse("package:$packageName")
                        requestExactAlarmSettingLauncher.launch(it)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[AddEdit] Cannot open exact alarm settings.", e)
                    Toast.makeText(this, "Не вдалося відкрити системні налаштування.", Toast.LENGTH_SHORT).show()
                    saveNoteInternal(scheduleAlarm = false) // Зберігаємо без планування
                }
            }
        } else {
            // Дозвіл є або не потрібен
            Log.d(TAG, "[AddEdit] Exact alarm permission granted or not needed. Proceeding to save.")
            saveNoteInternal()
        }
    }

    // Внутрішня функція збереження
    private fun saveNoteInternal(scheduleAlarm: Boolean = true) {
        Log.d(TAG, "[AddEdit] Executing saveNoteInternal. Schedule: $scheduleAlarm")
        val noteText = editTextNoteText.text.toString().trim()
        val timestamp = calendar.timeInMillis // Беремо час з calendar

        // Додаткова перевірка тексту
        if (noteText.isEmpty()) {
            Log.e(TAG, "[AddEdit] saveNoteInternal called with empty text!") // Не мало б статись
            return
        }

        val finalShouldSchedule = scheduleAlarm && timestamp > System.currentTimeMillis()
        Log.d(TAG, "[AddEdit] Final decision to schedule: $finalShouldSchedule")

        val noteToSave = Note(
            id = if (currentNoteId == INVALID_NOTE_ID) 0 else currentNoteId,
            text = noteText,
            timestamp = timestamp // Завжди зберігаємо обраний час
        )

        if (currentNoteId == INVALID_NOTE_ID) {
            // --- Додавання нової нотатки ---
            Log.d(TAG, "[AddEdit] Saving NEW note.")
            // Викликаємо insertNote і спостерігаємо за ID
            noteViewModel.insertNote(noteToSave).observe(this) { newId ->
                if (newId != null && newId > 0) {
                    Log.d(TAG, "[AddEdit] NEW note inserted with ID: $newId. Scheduling alarm: $finalShouldSchedule")
                    if (finalShouldSchedule) {
                        // Плануємо будильник ТУТ, коли вже маємо ID
                        noteViewModel.scheduleNoteAlarm(newId, timestamp)
                    }
                    Toast.makeText(this, R.string.note_saved_success, Toast.LENGTH_SHORT).show()
                    finish() // Закриваємо після успішного збереження та можливого планування
                } else if (newId != null) { // newId може бути 0 або -1 при помилці
                    Log.e(TAG, "[AddEdit] Error inserting NEW note, received ID: $newId")
                    Toast.makeText(this, R.string.error_saving_note, Toast.LENGTH_SHORT).show()
                    // Не закриваємо Activity, щоб користувач міг спробувати знову
                }
                // Видаляємо спостерігач після отримання результату, щоб уникнути повторних викликів
                // noteViewModel.insertNote(noteToSave).removeObservers(this) // ОБЕРЕЖНО! Може призвести до проблем
            }
        } else {
            // --- Оновлення існуючої нотатки ---
            Log.d(TAG, "[AddEdit] Updating EXISTING note ID: ${noteToSave.id}")
            noteViewModel.updateAndSchedule(noteToSave, oldTimestamp)
            Toast.makeText(this, R.string.note_updated_success, Toast.LENGTH_SHORT).show()
            finish() // Закриваємо після оновлення
        }
    }
    // --- Кінець логіки збереження ---

    // --- Діалоги дозволів ---
    private fun showPermissionRationaleDialog(title: String, message: String, onAccept: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> onAccept() }
            .setNegativeButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                Log.w(TAG, "[AddEdit] User cancelled rationale dialog for permission: $title")
                dialog.dismiss()
                // Можна зберегти без планування або нічого не робити
                saveNoteInternal(scheduleAlarm = false)
                Toast.makeText(this, R.string.saving_without_reminder, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPermissionDeniedDialog(title: String, message: String, onAccept: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> onAccept() }
            // Можна додати кнопку "Налаштування" для переходу в системні налаштування
            .setNeutralButton(getString(R.string.dialog_settings)) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "[AddEdit] Could not open app settings", e)
                    Toast.makeText(this, R.string.error_opening_settings, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    // --- Кінець діалогів ---

    // --- Date/Time Pickers ---
    private fun showDatePicker() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth)
            calendar.set(Calendar.DAY_OF_MONTH, selectedDay)
            Log.d(TAG, "[AddEdit] Date selected: ${calendar.time}")
            updateDateAndTimeViews()
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            calendar.set(Calendar.MINUTE, selectedMinute)
            // Опціонально: скинути секунди/мілісекунди
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            Log.d(TAG, "[AddEdit] Time selected: ${calendar.time}")
            updateDateAndTimeViews()
        }, hour, minute, true).show()
    }

    private fun updateDateAndTimeViews() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        textViewSelectedDate.text = dateFormat.format(calendar.time)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        textViewSelectedTime.text = timeFormat.format(calendar.time)
        Log.d(TAG, "[AddEdit] Updated Date/Time views: ${textViewSelectedDate.text} ${textViewSelectedTime.text}")
    }
    // --- Кінець Date/Time Pickers ---
}