package cz.olda.tasks.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.TaskEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [loaded] tells "not loaded yet" from "loaded, and there is no such task" ([task] null in both).
 * [headings] belong to the task's current list.
 */
data class TaskDetailState(
    val loaded: Boolean,
    val task: TaskEntity?,
    val subtasks: List<TaskEntity>,
    val lists: List<ListEntity>,
    val headings: List<HeadingEntity>,
)

/** One task: every editable field, its subtasks, completion and deletion. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
) : ViewModel() {

    private val task = taskRepository.observeTask(taskId)

    val state: StateFlow<TaskDetailState> = combine(
        task,
        taskRepository.observeSubtasks(taskId),
        listRepository.observeLists(),
        task.map { it?.listId }.distinctUntilChanged().flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList()) else listRepository.observeHeadings(listId)
        },
    ) { task, subtasks, lists, headings ->
        TaskDetailState(loaded = true, task = task, subtasks = subtasks, lists = lists, headings = headings)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TaskDetailState(loaded = false, task = null, subtasks = emptyList(), lists = emptyList(), headings = emptyList()),
    )

    // Each edit re-reads the stored task under this lock, so two quick edits (say title, then
    // notes) cannot write each other's old values back.
    private val editLock = Mutex()

    private fun edit(change: (TaskEntity) -> TaskEntity) {
        viewModelScope.launch {
            editLock.withLock {
                val current = taskRepository.observeTask(taskId).first() ?: return@withLock
                taskRepository.update(change(current))
            }
        }
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    /** A heading belongs to one list, so moving the task to another list clears it. */
    fun setList(listId: Long) = edit {
        if (it.listId == listId) it else it.copy(listId = listId, headingId = null)
    }

    fun setHeading(headingId: Long?) = edit { it.copy(headingId = headingId) }

    fun setDueDate(epochDay: Long?) = edit { it.copy(dueDate = epochDay) }

    fun setDueMinute(minute: Int?) = edit { it.copy(dueMinute = minute) }

    fun setPriority(priority: Int) = edit { it.copy(priority = priority) }

    /**
     * Ticks or unticks this task. Unticking here is a deliberate reopen, so it is setDone(false),
     * which leaves the subtasks as they are; the undo-after-completing path is undoCompletion.
     */
    fun toggleDone() {
        val current = state.value.task ?: return
        viewModelScope.launch { taskRepository.setDone(taskId, !current.done) }
    }

    fun toggleSubtaskDone(id: Long) {
        val sub = state.value.subtasks.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { taskRepository.setDone(id, !sub.done) }
    }

    /** Does nothing on a subtask: one level only. */
    fun addSubtask(title: String) {
        val parent = state.value.task ?: return
        if (parent.parentTaskId != null || title.isBlank()) return
        viewModelScope.launch {
            taskRepository.add(
                title = title.trim(),
                listId = parent.listId,
                dueDate = null,
                dueMinute = null,
                priority = 4,
                parentTaskId = parent.id,
            )
        }
    }

    /** Deletes the task and its subtasks; the screen goes back once this returns. */
    suspend fun delete() = taskRepository.softDelete(taskId)
}
