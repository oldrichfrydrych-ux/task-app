package cz.olda.tasks.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.SettingsRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.UpcomingDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What Upcoming shows. [days] is null until the database's first emission, so "nothing coming up"
 * never flashes on start.
 */
data class UpcomingState(
    val days: List<UpcomingDay>? = null,
    /** One choice, shared with Today and remembered by the phone. */
    val hideRepeats: Boolean = false,
)

/** Open tasks due after today, one entry per day. */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingViewModel(
    private val repository: TaskRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<UpcomingState> = settings
        .observeBoolean(SettingsRepository.HIDE_REPEATS, default = false)
        .flatMapLatest { hide ->
            repository.observeUpcoming(hideRepeats = hide).map { UpcomingState(it, hide) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpcomingState())

    /** The chip. Written to the settings table, so Today follows and the phone remembers it. */
    fun setHideRepeats(hide: Boolean) {
        viewModelScope.launch { settings.setBoolean(SettingsRepository.HIDE_REPEATS, hide) }
    }

    /** Completes the task. Screens call it through rememberCompletionUndo, never directly. */
    fun complete(id: Long) {
        viewModelScope.launch { repository.setDone(id, true) }
    }

    /** Reverses [complete], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { repository.undoCompletion(id) }
    }
}
