package cz.olda.tasks.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.entity.ListEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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
        // A list that was deleted since it was picked falls back to Inbox, like its tasks do.
        val listId = lists.firstOrNull { it.id == chosenList }?.id
            ?: lists.firstOrNull { it.isInbox }?.id
            ?: 0L
        QuickAddState(
            title = title,
            dueDate = if (epochDay == NO_DATE) null else LocalDate.ofEpochDay(epochDay),
            dueMinute = null,
            listId = listId,
            priority = priority,
            lists = lists,
            canSave = title.isNotBlank() && listId != 0L,
        )
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

    /** Writes the task and clears the draft. Does nothing while [QuickAddState.canSave] is false. */
    suspend fun save() {
        // Waits for the lists, so a save with nobody collecting [state] still sees the real draft.
        val current = state.first { it.listId != 0L }
        if (!current.canSave) return
        taskRepository.add(
            title = current.title.trim(),
            listId = current.listId,
            dueDate = current.dueDate,
            dueMinute = current.dueMinute,
            priority = current.priority,
        )
        clear()
    }

    private companion object {
        const val TITLE = "title"
        const val DATE = "date"
        const val LIST = "list"
        const val PRIORITY = "priority"
    }
}
