package com.example.tasksync.view

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tasksync.R
import com.example.tasksync.data.model.SyncStatus
import com.example.tasksync.data.model.Task

class TaskAdapter(
    private val onToggle: (Task, Boolean) -> Unit,
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cbCompleted: CheckBox = view.findViewById(R.id.cbCompleted)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        private val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        private val btnDelete: Button = view.findViewById(R.id.btnDelete)

        fun bind(task: Task) {
            tvTitle.text = task.title
            tvTitle.paintFlags = if (task.isCompleted) {
                tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            tvDescription.text = task.description
            tvDescription.isVisible = task.description.isNotBlank()

            // Gỡ listener trước khi đặt isChecked để việc tái sử dụng ô trong RecyclerView không bắn sự kiện giả
            cbCompleted.setOnCheckedChangeListener(null)
            cbCompleted.isChecked = task.isCompleted
            cbCompleted.setOnCheckedChangeListener { _, checked -> onToggle(task, checked) }

            tvStatus.setText(task.syncStatus.labelRes())
            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, task.syncStatus.colorRes()))

            itemView.setOnClickListener { onEdit(task) }
            btnDelete.setOnClickListener { onDelete(task) }
        }
    }

    private fun SyncStatus.labelRes(): Int = when (this) {
        SyncStatus.PENDING -> R.string.status_pending
        SyncStatus.SYNCING -> R.string.status_syncing
        SyncStatus.SYNCED -> R.string.status_synced
        SyncStatus.FAILED -> R.string.status_failed
    }

    private fun SyncStatus.colorRes(): Int = when (this) {
        SyncStatus.PENDING -> R.color.status_pending
        SyncStatus.SYNCING -> R.color.status_syncing
        SyncStatus.SYNCED -> R.color.status_synced
        SyncStatus.FAILED -> R.color.status_failed
    }

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(old: Task, new: Task) = old.id == new.id
        override fun areContentsTheSame(old: Task, new: Task) = old == new
    }
}
