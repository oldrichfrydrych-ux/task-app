package cz.olda.tasks.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.TaskWithList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The open tasks in the Inbox list. */
class InboxViewModel(
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
) : ViewModel() {

    /** Null until the first database emission, so "Inbox is empty" never flashes on start. */
    val state: StateFlow<List<TaskWithList>?> = flow {
        emitAll(taskRepository.observeInList(listRepository.inboxId()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Completes the task. Screens call it through rememberCompletionUndo, never directly. */
    fun complete(id: Long) {
        viewModelScope.launch { taskRepository.setDone(id, true) }
    }

    /** Reverses [complete], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { taskRepository.undoCompletion(id) }
    }
}
