package cz.olda.tasks.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.DaySections
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** What the Today screen shows: one day, the week around it and the dots on that week. */
data class TodayState(
    val shownDay: LocalDate,
    /** Overdue and Tomorrow only exist on today, so the screen keys them off this. */
    val isToday: Boolean,
    val sections: DaySections,
    val dots: Map<LocalDate, List<String>>,
    /** The Monday of the week that holds [shownDay]. */
    val weekStart: LocalDate,
)

/**
 * Owns which day Today shows. Unless the user has picked another day it follows the calendar:
 * a ticker reads the date from [clock] and fires again at the next local midnight, and every new
 * date re-collects the repository, which reads its own "today" only when a collection starts.
 */
class TodayViewModel(
    private val repository: TaskRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Null follows today; a date is the day the user picked. */
    private val picked = MutableStateFlow<LocalDate?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState> = combine(todayTicker(), picked) { today, pick ->
        today to (pick ?: today)
    }
        .flatMapLatest { (today, shown) ->
            val weekStart = shown.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            combine(
                repository.observeDay(shown),
                repository.observeDots(weekStart, weekStart.plusDays(6)),
            ) { sections, dots ->
                TodayState(shown, shown == today, sections, dots, weekStart)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    /** Picking today itself goes back to following the calendar. */
    fun showDay(date: LocalDate) {
        picked.value = if (date == LocalDate.now(clock)) null else date
    }

    /** Every row on Today is open, so toggling a row completes it. Screens go through rememberCompletionUndo. */
    fun toggleDone(id: Long) {
        viewModelScope.launch { repository.setDone(id, true) }
    }

    /** Reverses [toggleDone], subtasks included. */
    fun undoCompletion(id: Long) {
        viewModelScope.launch { repository.undoCompletion(id) }
    }

    /** Emits the current date, then again at each following local midnight. */
    private fun todayTicker(): Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock)
            emit(now.toLocalDate())
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone)
            delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1))
        }
    }.distinctUntilChanged()

    private fun initialState(): TodayState {
        val today = LocalDate.now(clock)
        return TodayState(
            shownDay = today,
            isToday = true,
            sections = DaySections(emptyList(), emptyList(), emptyList(), emptyList()),
            dots = emptyMap(),
            weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
        )
    }
}
