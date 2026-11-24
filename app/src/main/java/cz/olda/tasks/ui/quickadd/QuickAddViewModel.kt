package cz.olda.tasks.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.entity.ListEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** What the sheet shows. [listId] is 0 only until the lists have loaded. */
data class QuickAddState(
    val title: String,
    val dueDate: LocalDate?,
    val dueMinute: Int?,
    val listId: Long,
    val priority: Int,
    val lists: List<ListEntity>,
    val canSave: Boolean,
)

private const val NO_LIST = -1L
private const val NO_DATE = Long.MIN_VALUE
private const val DEFAULT_PRIORITY = 4

/**
 * The draft of the task being typed. The draft lives in [savedState], so it survives the process
 * being killed; the sheet calls [clear] when it closes, so the next one starts empty.
 */
class QuickAddViewModel(
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    val state: StateFlow<QuickAddState> = combine(
        savedState.getStateFlow(TITLE, ""),
        savedState.getStateFlow(DATE, NO_DATE),
        savedState.getStateFlow(LIST, NO_LIST),
        savedState.getStateFlow(PRIORITY, DEFAULT_PRIORITY),
        listRepository.observeLists(),
    ) { title, epochDay, chosenList, priority, lists ->
        build(title, epochDay, chosenList, priority, lists)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        QuickAddState("", null, null, 0L, DEFAULT_PRIORITY, emptyList(), canSave = false),
    )

    fun setTitle(title: String) { savedState[TITLE] = title }

    fun setDate(date: LocalDate?) { savedState[DATE] = date?.toEpochDay() ?: NO_DATE }

    fun setList(listId: Long) { savedState[LIST] = listId }

    fun setPriority(priority: Int) { savedState[PRIORITY] = priority }

    /** Throws the draft away. */
    fun clear() {
        savedState[TITLE] = ""
        savedState[DATE] = NO_DATE
        savedState[LIST] = NO_LIST
        savedState[PRIORITY] = DEFAULT_PRIORITY
    }

    /** The title as stored, for the field to start from after a restart (the [state] flow may not have collected yet). */
    val draftTitle: String get() = savedState[TITLE] ?: ""

    // One write at a time: a second tap, or a tap plus IME Done, waits here and then finds the draft gone.
    private val saveLock = Mutex()

    private fun build(title: String, epochDay: Long, chosenList: Long, priority: Int, lists: List<ListEntity>): QuickAddState {
        // A list that was deleted since it was picked falls back to Inbox, like its tasks do.
        val listId = lists.firstOrNull { it.id == chosenList }?.id
            ?: lists.firstOrNull { it.isInbox }?.id
            ?: 0L
        return QuickAddState(
            title = title,
            dueDate = if (epochDay == NO_DATE) null else LocalDate.ofEpochDay(epochDay),
            dueMinute = null,
            listId = listId,
            priority = priority,
            lists = lists,
            canSave = title.isNotBlank() && listId != 0L,
        )
    }

    /**
     * Writes the task and clears the draft; true only if a task was written. The write runs in
     * [viewModelScope], so leaving the screen or rotating mid-write cannot cancel it half way.
     * A second call while one is running waits, then finds no draft and returns false. A failed
     * write keeps the draft and returns false.
     */
    suspend fun save(): Boolean = viewModelScope.async {
        saveLock.withLock {
            // Read straight from the handle, not [state], which may not have caught up with a clear().
            val current = build(
                savedState[TITLE] ?: "",
                savedState[DATE] ?: NO_DATE,
                savedState[LIST] ?: NO_LIST,
                savedState[PRIORITY] ?: DEFAULT_PRIORITY,
                listRepository.observeLists().first(),
            )
            if (!current.canSave) return@withLock false
            try {
                taskRepository.add(
                    title = current.title.trim(),
                    listId = current.listId,
                    dueDate = current.dueDate,
                    dueMinute = current.dueMinute,
                    priority = current.priority,
                )
            } catch (e: Exception) {
                return@withLock false
            }
            clear()
            true
        }
    }.await()

    internal companion object {
        const val TITLE = "title"
        const val DATE = "date"
        const val LIST = "list"
        const val PRIORITY = "priority"
    }
}
