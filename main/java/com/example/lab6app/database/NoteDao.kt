package com.example.lab6app.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.lab6app.model.Note

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes_table ORDER BY timestamp ASC")
    fun getAllNotes(): LiveData<List<Note>>

    @Query("SELECT * FROM notes_table WHERE id = :noteId")
    suspend fun getNoteById(noteId: Int): Note?

    @Query("SELECT * FROM notes_table")
    suspend fun getAllNotesList(): List<Note>

    @Query("SELECT * FROM notes_table WHERE timestamp > :currentTime ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextUpcomingNote(currentTime: Long): Note?
}
