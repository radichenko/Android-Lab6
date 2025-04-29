package com.example.lab6app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.lab6app.R
import com.example.lab6app.model.Note

class NoteAdapter(
    private val onItemClicked: (Note) -> Unit,
    private val onDeleteClicked: (Note) -> Unit
) : ListAdapter<Note, NoteAdapter.NoteViewHolder>(NoteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = getItem(position)
        holder.bind(currentNote, onItemClicked, onDeleteClicked)
    }

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val noteTextView: TextView = itemView.findViewById(R.id.textViewNoteText)
        private val timestampTextView: TextView = itemView.findViewById(R.id.textViewNoteTimestamp)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteNote)

        fun bind(note: Note, onItemClicked: (Note) -> Unit, onDeleteClicked: (Note) -> Unit) {
            noteTextView.text = note.text
            timestampTextView.text = note.getFormattedTimestamp()

            itemView.setOnClickListener {
                onItemClicked(note)
            }

            deleteButton.setOnClickListener {
                onDeleteClicked(note)
            }
        }
    }

    class NoteDiffCallback : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}