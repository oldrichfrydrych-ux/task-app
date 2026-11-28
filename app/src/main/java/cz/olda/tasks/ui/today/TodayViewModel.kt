package cz.olda.tasks.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.DaySections
import cz.olda.tasks.data.SettingsRepository
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

private data class Pick(val day: LocalDate, val madeOn: LocalDate)

/** What the Today screen shows: one day, the week around it and the dots on that week. */
data class TodayState(
    val shownDay: LocalDate,
    /** Overdue and Tomorrow only exist on today, so the screen keys them off this. */
    val isToday: Boolean,
    val sections: DaySections,
    val dots: Map<LocalDate, List<String>>,
    /** The Monday of the week that holds [shownDay]. */
    val weekStart: LocalDate,
    /**
     * How many of the shown day's tasks are already done. They are not in [sections] - completing
     * a task takes it off the screen - so this is the only trace of them, and it is what makes
     * the glance line's "2 of 7 done" possible.
     */
    val doneCount: Int = 0,
    /**
     * False only for the placeholder state before the database's first emission. Without it an
     * empty day and a not-yet-loaded one look the same, and "Nothing due today" flashes on start.
     */
    val isLoaded: Boolean = true,
    /**
     * Whether repeating tasks are out of the way. One choice, shared with Upcoming and remembered
     * by the phone: wanting repeats hidden is a mood, not a per-screen preference.
     */
    val hideRepeats: Boolean = false,
)

/**
 * Owns which day Today shows. Unless the user has picked another day it follows the calendar:
 * a ticker reads the date from [clock] and fires again at the next local midnight, and every new
 * date re-collects the repository, which reads its own "today" only when a collection starts.
 */
class TodayViewModel(
    private val repository: TaskRepository,
    private val clock: Clock,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val hideRepeats = settings.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false)

    /**
     * Null follows today. A pick remembers the date it was made on and only counts while that is
     * still today, so crossing midnight returns to today instead of stranding the user on a day
     * whose week may no longer hold today.
     */
    private val picked = MutableStateFlow<Pick?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState> = combine(todayTicker(), picked, hideRepeats) { today, pick, hide ->
        Triple(today, pick?.takeIf { it.madeOn == today }?.day ?: today, hide)
    }
        .flatMapLatest { (today, shown, hide) ->
            val weekStart = shown.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            combine(
                repository.observeDay(shown, hideRepeats = hide),
                // The dots are deliberately not filtered: a day is coloured by what is due on it,
                // not by what is currently on screen.
                repository.observeDots(weekStart, weekStart.plusDays(6)),
                repository.observeDoneCount(shown),
            ) { sections, dots, doneCount ->
                TodayState(shown, shown == today, sections, dots, weekStart, doneCount, hideRepeats = hide)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    /** Picking today itself goes back to following the calendar. */
    fun showDay(date: LocalDate) {
        val today = LocalDate.now(clock)
        picked.value = if (date == today) null else Pick(date, madeOn = today)
    }

    /** The way back from any picked day. */
    fun showToday() = showDay(LocalDate.now(clock))

    /** The chip. Written to the settings table, so Upcoming follows and the phone remembers it. */
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
            isLoaded = false,
        )
    }
}
