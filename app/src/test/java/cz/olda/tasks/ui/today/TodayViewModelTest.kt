package cz.olda.tasks.ui.today

import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.dao.DayDot
import cz.olda.tasks.data.dao.ListOpenCount
import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.TaskEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test can move; the zone is fixed so "midnight" is unambiguous. */
private class MutableClock(private var instant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId?): Clock = MutableClock(instant, zone ?: this.zone)
    override fun instant(): Instant = instant
    fun advanceBy(duration: Duration) {
        instant = instant.plus(duration)
    }
}

/** Serves only the day queries; every day query is keyed by the epoch day it is asked about. */
internal class FakeTaskDao(private val overdue: List<TaskWithList>) : TaskDao() {
    val dayQueries = mutableListOf<Pair<Long, Long>>()

    override fun observeOverdue(epochDay: Long): Flow<List<TaskWithList>> = flowOf(overdue)
    override fun observeDueBetween(fromDay: Long, toDay: Long): Flow<List<TaskWithList>> {
        dayQueries += fromDay to toDay
        return flowOf(emptyList())
    }

    override fun observeDots(fromDay: Long, toDay: Long): Flow<List<DayDot>> = flowOf(emptyList())

    override fun observeDueAfter(epochDay: Long): Flow<List<TaskWithList>> = flowOf(emptyList())
    override fun observeInList(listId: Long): Flow<List<TaskWithList>> = TODO()
    override fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>> = TODO()
    override fun observeTask(id: Long): Flow<TaskEntity?> = TODO()
    override suspend fun getTask(id: Long): TaskEntity? = TODO()
    override fun search(pattern: String): Flow<List<TaskWithList>> = TODO()
    override fun observeOpenCounts(): Flow<List<ListOpenCount>> = TODO()
    override suspend fun nextPosition(listId: Long, headingId: Long?, parentTaskId: Long?): Int = TODO()
    override suspend fun insert(task: TaskEntity): Long = TODO()
    override suspend fun updateEditable(
        id: Long, title: String, titleNormalised: String, notes: String, notesNormalised: String,
        listId: Long, headingId: Long?, dueDate: Long?, dueMinute: Int?, priority: Int, now: Long,
    ): Unit = TODO()
    override suspend fun followParent(parentId: Long, now: Long): Unit = TODO()
    override suspend fun markDone(id: Long, done: Boolean, completedAt: Long?, now: Long): Unit = TODO()
    override suspend fun completedAtOf(id: Long): Long? = TODO()
    override suspend fun reopenStamped(id: Long, stamp: Long, now: Long): Unit = TODO()
    override suspend fun completeOpenSubtasks(parentId: Long, now: Long): Unit = TODO()
    override suspend fun deletedAtOf(id: Long): Long? = TODO()
    override suspend fun stampDeleted(id: Long, stamp: Long): Unit = TODO()
    override suspend fun clearDeleted(id: Long, stamp: Long, now: Long): Unit = TODO()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 11, 23)

    private fun startOfDay(isoLocal: String): Instant =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant()

    @Before fun setMain() = Dispatchers.setMain(StandardTestDispatcher())

    @After fun resetMain() = Dispatchers.resetMain()

    private fun overdueRow() = TaskWithList(
        task = TaskEntity(
            id = 1, title = "late", titleNormalised = "late", notesNormalised = "", listId = 1,
            dueDate = today.minusDays(1).toEpochDay(),
        ),
        listName = "Inbox",
        listColorHex = "#E85A4F",
    )

    private fun TestScope.viewModel(clock: Clock, dao: FakeTaskDao = FakeTaskDao(listOf(overdueRow()))): TodayViewModel {
        val vm = TodayViewModel(TaskRepository(dao, clock), clock)
        // WhileSubscribed state only moves while something collects it, as the screen does.
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    @Test fun isNotLoadedUntilTheFirstEmission() = runTest {
        val clock = MutableClock(startOfDay("2026-11-23T10:00"), zone)
        val vm = TodayViewModel(TaskRepository(FakeTaskDao(emptyList()), clock), clock)
        backgroundScope.launch { vm.state.collect {} }
        assertFalse(vm.state.value.isLoaded) // nothing has run yet: placeholder, not "empty"
        runCurrent()
        assertTrue(vm.state.value.isLoaded)
    }

    @Test fun startsOnToday() = runTest {
        val vm = viewModel(MutableClock(startOfDay("2026-11-23T10:00"), zone))
        val state = vm.state.value
        assertEquals(today, state.shownDay)
        assertTrue(state.isToday)
        assertEquals(listOf("late"), state.sections.overdue.map { it.task.title })
        assertEquals(LocalDate.of(2026, 11, 23), state.weekStart) // a Monday
    }

    @Test fun pickingADayDropsOverdueAndTomorrow() = runTest {
        val vm = viewModel(MutableClock(startOfDay("2026-11-23T10:00"), zone))
        assertEquals(1, vm.state.value.sections.overdue.size)
        vm.showDay(today.plusDays(2))
        runCurrent()
        assertEquals(today.plusDays(2), vm.state.value.shownDay)
        assertFalse(vm.state.value.isToday)
        assertTrue(vm.state.value.sections.overdue.isEmpty())
    }

    @Test fun midnightMovesTheShownDay() = runTest {
        val clock = MutableClock(startOfDay("2026-11-23T23:59"), zone)
        val dao = FakeTaskDao(emptyList())
        val vm = viewModel(clock, dao)
        assertEquals(today, vm.state.value.shownDay)
        assertEquals(today.toEpochDay() to today.toEpochDay(), dao.dayQueries.last())

        // The wall clock and the ticker's virtual time pass together, as they would for real.
        clock.advanceBy(Duration.ofMinutes(2))
        advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()

        val tomorrow = LocalDate.parse("2026-11-24")
        assertEquals(tomorrow, vm.state.value.shownDay)
        assertTrue(vm.state.value.isToday)
        // The repository read the new date when it was collected again, not the old one.
        assertEquals(tomorrow.toEpochDay() to tomorrow.toEpochDay(), dao.dayQueries.last())
    }

    /** Sunday into Monday: the pick falls in a week that no longer holds today. */
    @Test fun aPickedDayDoesNotStrandTheUserAcrossAWeekBoundary() = runTest {
        val clock = MutableClock(startOfDay("2026-11-29T23:59"), zone) // Sunday
        val vm = viewModel(clock)
        vm.showDay(LocalDate.parse("2026-11-27"))
        runCurrent()
        assertEquals(LocalDate.parse("2026-11-27"), vm.state.value.shownDay)
        assertFalse(vm.state.value.isToday)

        clock.advanceBy(Duration.ofMinutes(2))
        advanceTimeBy(Duration.ofMinutes(2).toMillis())
        runCurrent()

        val monday = LocalDate.parse("2026-11-30")
        assertEquals(monday, vm.state.value.shownDay)
        assertTrue(vm.state.value.isToday)
        assertEquals(monday, vm.state.value.weekStart)
    }

    @Test fun pickingTodayFollowsTheCalendarAgain() = runTest {
        val clock = MutableClock(startOfDay("2026-11-23T23:59"), zone)
        val vm = viewModel(clock)
        vm.showDay(today.plusDays(1))
        vm.showDay(today)
        runCurrent()
        assertTrue(vm.state.value.isToday)
    }
}
