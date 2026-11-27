package cz.olda.tasks.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TagRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.TagEntity
import cz.olda.tasks.data.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    val tags: List<TagEntity>,
)

/** One task: every editable field, its subtasks, completion and deletion. */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val taskId: Long,
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
    private val tagRepository: TagRepository,
    /** Outlives the ViewModel, so text typed just before leaving the screen is still written. */
    private val flushScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ViewModel() {

    private val task = taskRepository.observeTask(taskId)

    val state: StateFlow<TaskDetailState> = combine(
        task,
        taskRepository.observeSubtasks(taskId),
        listRepository.observeLists(),
        task.map { it?.listId }.distinctUntilChanged().flatMapLatest { listId ->
            if (listId == null) flowOf(emptyList()) else listRepository.observeHeadings(listId)
        },
        tagRepository.observeTagsOf(taskId),
    ) { task, subtasks, lists, headings, tags ->
        TaskDetailState(
            loaded = true,
            task = task,
            subtasks = subtasks,
            lists = lists,
            headings = headings,
            tags = tags,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TaskDetailState(
            loaded = false,
            task = null,
            subtasks = emptyList(),
            lists = emptyList(),
            headings = emptyList(),
            tags = emptyList(),
        ),
    )

    // Each edit re-reads the stored task under this lock, so two quick edits (say title, then
    // notes) cannot write each other's old values back.
    private val editLock = Mutex()

    private suspend fun applyEdit(change: (TaskEntity) -> TaskEntity) {
        editLock.withLock {
            val current = taskRepository.getTask(taskId) ?: return@withLock
            val changed = change(current)
            if (changed != current) taskRepository.update(changed)
        }
    }

    private fun edit(change: (TaskEntity) -> TaskEntity) {
        viewModelScope.launch { applyEdit(change) }
    }

    // Text is written once typing pauses, not per character: a 200-character note would otherwise
    // be 200 transactions. What has not been written yet is [pendingTitle] / [pendingNotes]; they
    // are touched on the main thread only.
    private var pendingTitle: String? = null
    private var pendingNotes: String? = null
    private var titleJob: Job? = null
    private var notesJob: Job? = null

    fun setTitle(title: String) {
        pendingTitle = title
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            val text = pendingTitle ?: return@launch
            pendingTitle = null
            withContext(NonCancellable) { applyEdit { it.copy(title = text) } }
        }
    }

    fun setNotes(notes: String) {
        pendingNotes = notes
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            val text = pendingNotes ?: return@launch
            pendingNotes = null
            withContext(NonCancellable) { applyEdit { it.copy(notes = text) } }
        }
    }

    /** A heading belongs to one list, so moving to another list clears it; subtasks follow their parent instead. */
    fun setList(listId: Long) = edit {
        if (it.parentTaskId != null || it.listId == listId) it else it.copy(listId = listId, headingId = null)
    }

    fun setHeading(headingId: Long?) = edit { if (it.parentTaskId != null) it else it.copy(headingId = headingId) }

    fun setDueDate(epochDay: Long?) = edit { it.copy(dueDate = epochDay) }

    fun setDueMinute(minute: Int?) = edit { it.copy(dueMinute = minute) }

    fun setPriority(priority: Int) = edit { it.copy(priority = priority) }

    /**
     * Throws away a repeat. Phase 2 only has to let Olda get rid of one he did not mean to type;
     * **changing** a repeat waits for Phase 3, which builds the picker anyway.
     */
    fun clearRecurrence() = edit { it.copy(recurrence = null) }

    fun addTag(name: String) {
        val cleaned = name.trim().removePrefix("@")
        if (cleaned.isBlank()) return
        viewModelScope.launch { tagRepository.attach(taskId, listOf(cleaned)) }
    }

    fun removeTag(tagId: Long) {
        viewModelScope.launch { tagRepository.detach(taskId, tagId) }
    }

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

    /** Leaving the screen inside the typing pause must not lose the last keystrokes. */
    override fun onCleared() {
        val title = pendingTitle
        val notes = pendingNotes
        if (title == null && notes == null) return
        flushScope.launch {
            applyEdit { task ->
                task.copy(title = title ?: task.title, notes = notes ?: task.notes)
            }
        }
    }

    /** Deletes the task and its subtasks; the screen goes back once this returns. */
    suspend fun delete() = taskRepository.softDelete(taskId)
}

/** How long typing must pause before the title or notes are written. */
private const val TYPING_PAUSE_MS = 300L
