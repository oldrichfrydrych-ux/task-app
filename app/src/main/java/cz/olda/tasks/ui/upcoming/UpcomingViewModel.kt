package cz.olda.tasks.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.UpcomingDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Open tasks due after today, one entry per day. */
class UpcomingViewModel(private val repository: TaskRepository) : ViewModel() {

    /** Null until the first database emission, so "nothing coming up" never flashes on start. */
    val state: StateFlow<List<UpcomingDay>?> = repository.observeUpcoming()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Completes the task. Screens call it through rememberCompletionUndo, never directly. */
    fun complete(id: Long) {
        viewModelScope.launch { repository.setDone(id, true) }
    }

    /** Reverses [complete], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { repository.undoCompletion(id) }
    }
}
