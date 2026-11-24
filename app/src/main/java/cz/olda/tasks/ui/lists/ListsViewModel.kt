package cz.olda.tasks.ui.lists

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.GroupWithLists
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The groups and lists the user organises tasks with, and every way of changing them. */
class ListsViewModel(
    private val listRepository: ListRepository,
    taskRepository: TaskRepository,
) : ViewModel() {

    /**
     * Groups with their lists, Inbox left out: it is not organised here. A null group holds the
     * ungrouped lists and is dropped when it holds none. Null until the first database emission,
     * so "no lists yet" never flashes on start.
     */
    val state: StateFlow<List<GroupWithLists>?> = listRepository.observeGroupsWithLists()
        .map { groups ->
            groups
                .map { it.copy(lists = it.lists.filterNot { list -> list.isInbox }) }
                .filterNot { it.group == null && it.lists.isEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Open top-level tasks per list id; null until loaded, and a list missing from it has none. */
    val openCounts: StateFlow<Map<Long, Int>?> = taskRepository.observeOpenCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Launches a write without letting a failure take the app down:
     * a failed write (say a colour that cannot be parsed) is logged and the screen simply shows the
     * unchanged data, which the database flows keep up to date.
     */
    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ListsViewModel", "Write failed", e)
            }
        }
    }

    /** A null [colorHex] takes the next preset colour. */
    fun createList(name: String, colorHex: String?, groupId: Long?) {
        guarded { listRepository.createList(name.trim(), colorHex, groupId) }
    }

    fun renameList(id: Long, name: String) {
        guarded { listRepository.renameList(id, name.trim()) }
    }

    fun recolourList(id: Long, colorHex: String) {
        guarded { listRepository.recolourList(id, colorHex) }
    }

    /** Moves the list into a group, or out of any with null; the Inbox is refused by the repository. */
    fun moveListToGroup(id: Long, groupId: Long?) {
        guarded { listRepository.moveListToGroup(id, groupId) }
    }

    /** The list's tasks move to Inbox; the screen says so before asking for this. */
    fun deleteList(id: Long) {
        guarded { listRepository.deleteList(id) }
    }

    fun createGroup(name: String) {
        guarded { listRepository.createGroup(name.trim()) }
    }

    fun renameGroup(id: Long, name: String) {
        guarded { listRepository.renameGroup(id, name.trim()) }
    }

    /** The group's lists stay, outside any group. */
    fun deleteGroup(id: Long) {
        guarded { listRepository.deleteGroup(id) }
    }
}
