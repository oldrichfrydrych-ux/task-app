package cz.olda.tasks.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.TaskWithList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the search screen shows: the text typed so far and the open tasks it matches. */
data class SearchState(val query: String, val results: List<TaskWithList>)

/** Searches open tasks by title and notes; the matching rules live in [TaskRepository.search]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: TaskRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<SearchState> = query
        .flatMapLatest { q -> repository.search(q).map { SearchState(q, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState("", emptyList()))

    fun setQuery(query: String) = this.query.update { query }

    /** Completes the task. Screens call it through rememberCompletionUndo, never directly. */
    fun complete(id: Long) {
        viewModelScope.launch { repository.setDone(id, true) }
    }

    /** Reverses [complete], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { repository.undoCompletion(id) }
    }
}
