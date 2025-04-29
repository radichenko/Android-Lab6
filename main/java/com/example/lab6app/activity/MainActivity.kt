package com.example.lab6app.activity


import android.content.Intent
import android.os.Bundle
import android.util.Log // Імпорт логування
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager // Додано, якщо немає в XML
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.example.lab6app.R
import com.example.lab6app.adapter.NoteAdapter
import com.example.lab6app.viewmodel.NoteViewModel

class MainActivity : AppCompatActivity() {

    private val noteViewModel: NoteViewModel by viewModels()
    private val TAG = "NoteReminderApp" // Тег для логів

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "[Main] onCreate called.")

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val fab = findViewById<FloatingActionButton>(R.id.fabAddNote)

        val adapter = NoteAdapter(
            onItemClicked = { note ->
                Log.d(TAG, "[Main] Note item clicked, ID: ${note.id}")
                val intent = Intent(this, AddEditNoteActivity::class.java)
                intent.putExtra(AddEditNoteActivity.EXTRA_NOTE_ID, note.id)
                startActivity(intent)
            },
            onDeleteClicked = { note ->
                Log.d(TAG, "[Main] Delete button clicked for note ID: ${note.id}")
                noteViewModel.deleteAndCancel(note)

                Snackbar.make(recyclerView, R.string.note_deleted_message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo_action) {
                        Log.d(TAG, "[Main] UNDO action clicked for note ID: ${note.id}")
                        // Викликаємо метод для відновлення (з потенційними проблемами ID, як зазначено у ViewModel)
                        noteViewModel.insertAndScheduleForUndo(note)
                    }.show()
            }
        )

        recyclerView.adapter = adapter
        // Переконайтесь, що LayoutManager встановлено (або тут, або в XML)
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = LinearLayoutManager(this)
        }


        Log.d(TAG, "[Main] Setting up observer for allNotes.")
        noteViewModel.allNotes.observe(this) { notes ->
            notes?.let {
                Log.d(TAG, "[Main] Observer received ${it.size} notes. Submitting list.")
                adapter.submitList(it)
            } ?: Log.w(TAG, "[Main] Observer received null list.")
        }

        fab.setOnClickListener {
            Log.d(TAG, "[Main] FAB clicked - launching AddEditNoteActivity for new note.")
            val intent = Intent(this, AddEditNoteActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "[Main] onResume called.")
        // Тут можна додати логіку оновлення, якщо потрібно
    }
}