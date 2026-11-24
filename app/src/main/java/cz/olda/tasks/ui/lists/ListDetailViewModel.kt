package cz.olda.tasks.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One heading and the open tasks filed under it; [tasks] may be empty. */
data class HeadingSection(val heading: HeadingEntity, val tasks: List<TaskWithList>)

/** [list] is null until the list has loaded (or if it no longer exists). */
data class ListDetailState(
    val list: ListEntity?,
    val unheaded: List<TaskWithList>,
    val sections: List<HeadingSection>,
)

/** One list: its tasks that sit under no heading, then each heading with its own tasks. */
class ListDetailViewModel(
    private val listId: Long,
    private val listRepository: ListRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val state: StateFlow<ListDetailState> = combine(
        listRepository.observeLists().map { all -> all.firstOrNull { it.id == listId } },
        listRepository.observeHeadings(listId),
        taskRepository.observeInList(listId),
    ) { list, headings, tasks ->
        val byHeading = tasks.groupBy { it.task.headingId }
        ListDetailState(
            list = list,
            unheaded = byHeading[null].orEmpty(),
            sections = headings.map { HeadingSection(it, byHeading[it.id].orEmpty()) },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ListDetailState(list = null, unheaded = emptyList(), sections = emptyList()),
    )

    fun addHeading(name: String) {
        viewModelScope.launch { listRepository.createHeading(listId, name) }
    }

    fun renameHeading(id: Long, name: String) {
        viewModelScope.launch { listRepository.renameHeading(id, name) }
    }

    /** The heading's tasks stay in the list, without a heading. */
    fun deleteHeading(id: Long) {
        viewModelScope.launch { listRepository.deleteHeading(id) }
    }

    /** Completes the task. Screens call it through rememberCompletionUndo, never directly. */
    fun complete(id: Long) {
        viewModelScope.launch { taskRepository.setDone(id, true) }
    }

    /** Reverses [complete], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { taskRepository.undoCompletion(id) }
    }
}
