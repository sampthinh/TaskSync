package com.example.tasksync.view

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tasksync.R
import com.example.tasksync.data.model.Task
import com.example.tasksync.sync.SyncUiState
import com.example.tasksync.viewmodel.TaskViewModel
import com.example.tasksync.viewmodel.TaskViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: TaskViewModel by viewModels { TaskViewModelFactory(application) }

    private lateinit var adapter: TaskAdapter
    private lateinit var rvTasks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSyncBanner: TextView
    private lateinit var btnSync: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyWindowInsets()

        rvTasks = findViewById(R.id.rvTasks)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSyncBanner = findViewById(R.id.tvSyncBanner)
        btnSync = findViewById(R.id.btnSync)

        adapter = TaskAdapter(
            onToggle = { task, checked -> viewModel.setCompleted(task.id, checked) },
            onEdit = { task -> showTaskDialog(task) },
            onDelete = { task -> confirmDelete(task) }
        )
        rvTasks.layoutManager = LinearLayoutManager(this)
        rvTasks.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rvTasks.adapter = adapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener { showTaskDialog(null) }
        btnSync.setOnClickListener { viewModel.syncNow() }

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { tasks ->
                        adapter.submitList(tasks)
                        rvTasks.isVisible = tasks.isNotEmpty()
                        tvEmpty.isVisible = tasks.isEmpty()
                    }
                }
                launch {
                    viewModel.syncState.collect { renderSyncState(it) }
                }
            }
        }
    }

    private fun renderSyncState(state: SyncUiState) {
        val (textRes, colorRes) = when (state) {
            SyncUiState.RUNNING -> R.string.banner_running to R.color.status_syncing
            SyncUiState.WAITING -> R.string.banner_waiting to R.color.status_pending
            SyncUiState.SUCCEEDED -> R.string.banner_succeeded to R.color.status_synced
            SyncUiState.FAILED -> R.string.banner_failed to R.color.status_failed
            SyncUiState.IDLE -> null to null
        }
        tvSyncBanner.isVisible = textRes != null
        if (textRes != null && colorRes != null) {
            tvSyncBanner.setText(textRes)
            tvSyncBanner.setTextColor(ContextCompat.getColor(this, colorRes))
        }
        val running = state == SyncUiState.RUNNING
        btnSync.isEnabled = !running
        btnSync.setText(if (running) R.string.btn_syncing else R.string.btn_sync)
    }

    private fun showTaskDialog(task: Task?) {
        val content = layoutInflater.inflate(R.layout.dialog_task, null)
        val etTitle = content.findViewById<EditText>(R.id.etTitle)
        val etDescription = content.findViewById<EditText>(R.id.etDescription)
        if (task != null) {
            etTitle.setText(task.title)
            etDescription.setText(task.description)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (task == null) R.string.dialog_add_title else R.string.dialog_edit_title)
            .setView(content)
            .setPositiveButton(R.string.action_save, null)   // gắn sau để nhập thiếu thì không tự đóng
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val title = etTitle.text.toString().trim()
                val description = etDescription.text.toString().trim()
                if (title.isEmpty()) {
                    etTitle.error = getString(R.string.error_title_required)
                    return@setOnClickListener
                }
                when {
                    task == null -> viewModel.addTask(title, description)
                    title != task.title || description != task.description ->
                        viewModel.updateTask(task.id, title, description)
                    // không đổi gì thì không cần ghi và không cần đồng bộ
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(task: Task) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, task.title))
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteTask(task.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.main)
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            insets
        }
    }
}