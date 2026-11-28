package cz.olda.tasks.data

import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.data.recurrence.nextOccurrence
import cz.olda.tasks.data.reminder.Ring
import cz.olda.tasks.data.reminder.nextRing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * The sections of one day; [overdue] and [tomorrow] are empty unless the day is today.
 *
 * [hiddenRepeats] holds the repeating tasks taken out of [timed] and [anytime] when repeats are
 * hidden. They are moved aside rather than dropped, so the folded row can count them and a tap can
 * show them without a second read — and so nothing can disappear silently, which is what
 * docs/design.md asks for.
 */
data class DaySections(
    val overdue: List<TaskWithList>,
    val timed: List<TaskWithList>,
    val anytime: List<TaskWithList>,
    val tomorrow: List<TaskWithList>,
    val hiddenRepeats: List<TaskWithList> = emptyList(),
)

data class UpcomingDay(
    val date: LocalDate,
    val tasks: List<TaskWithList>,
    val hiddenRepeats: List<TaskWithList> = emptyList(),
)

class TaskRepository(
    private val taskDao: TaskDao,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    private fun now(): Long = clock.millis()

    /**
     * "Today" is read when the flow is collected, not when it is created.
     *
     * With [hideRepeats], a task that repeats leaves [DaySections.timed] and
     * [DaySections.anytime] for [DaySections.hiddenRepeats], keeping the order it had.
     * [DaySections.overdue] is never filtered — something already missed is exactly what hiding
     * must not swallow — and neither is [DaySections.tomorrow], which is one line standing for a
     * whole day and would otherwise disagree with that day when it is opened.
     */
    fun observeDay(day: LocalDate, hideRepeats: Boolean = false): Flow<DaySections> = flow {
        val today = today()
        val isToday = day == today
        val overdue = if (isToday) taskDao.observeOverdue(today.toEpochDay()) else flowOf(emptyList())
        val tomorrow = if (isToday) {
            taskDao.observeDueBetween(today.toEpochDay() + 1, today.toEpochDay() + 1)
        } else {
            flowOf(emptyList())
        }
        val onDay = taskDao.observeDueBetween(day.toEpochDay(), day.toEpochDay())
        emitAll(
            combine(overdue, onDay, tomorrow) { o, d, t ->
                val shown = if (hideRepeats) d.filter { it.task.recurrence == null } else d
                DaySections(
                    overdue = o,
                    timed = shown.filter { it.task.dueMinute != null },
                    anytime = shown.filter { it.task.dueMinute == null },
                    tomorrow = t,
                    hiddenRepeats = if (hideRepeats) d.filter { it.task.recurrence != null } else emptyList(),
                )
            },
        )
    }

    /**
     * Open tasks due after today, grouped by day, earliest first. With [hideRepeats] each day's
     * repeating tasks move to [UpcomingDay.hiddenRepeats], so every day gets its own folded row and
     * a day of nothing but repeats still appears.
     */
    fun observeUpcoming(hideRepeats: Boolean = false): Flow<List<UpcomingDay>> = flow {
        emitAll(
            taskDao.observeDueAfter(today().toEpochDay()).map { rows ->
                rows.groupBy { it.task.dueDate!! }
                    .map { (epochDay, tasks) ->
                        UpcomingDay(
                            date = LocalDate.ofEpochDay(epochDay),
                            tasks = if (hideRepeats) tasks.filter { it.task.recurrence == null } else tasks,
                            hiddenRepeats = if (hideRepeats) {
                                tasks.filter { it.task.recurrence != null }
                            } else {
                                emptyList()
                            },
                        )
                    }
            },
        )
    }

    /**
     * Open top-level tasks only. Day views, dots and search also include subtasks, so a subtask can
     * show on Today without appearing here.
     */
    fun observeInList(listId: Long): Flow<List<TaskWithList>> = taskDao.observeInList(listId)

    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>> = taskDao.observeSubtasks(parentId)

    fun observeTask(id: Long): Flow<TaskEntity?> = taskDao.observeTask(id)

    /** One read of a live task, without opening a flow: for code that needs the current row once. */
    suspend fun getTask(id: Long): TaskEntity? = taskDao.getTask(id)

    /** The same, with the name of the task's list, which a reminder has to say and cannot look up. */
    suspend fun getTaskWithList(id: Long): TaskWithList? = taskDao.getTaskWithList(id)

    /** The distinct list colours with an open task (subtasks included) on each day in [from]..[to] inclusive. */
    fun observeDots(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<String>>> =
        taskDao.observeDots(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.groupBy({ LocalDate.ofEpochDay(it.dueDate) }, { it.colorHex })
        }

    /** How many of [day]'s tasks are already done; the open ones come from [observeDay]. */
    fun observeDoneCount(day: LocalDate): Flow<Int> = taskDao.observeDoneCount(day.toEpochDay())

    /**
     * The one ring to book: the earliest moment a reminder is due, with every task that shares it.
     *
     * Room re-runs the query behind this on every write to `tasks`, so adding a task, moving its
     * date, snoozing it, completing it or deleting it all re-emit here, and whatever books the
     * alarm never has to be told. "Now" is read at each emission, and a ring whose minute has
     * already passed is left out, which is what makes re-booking idempotent — see
     * [cz.olda.tasks.data.reminder.nextRing].
     */
    fun observeNextReminder(): Flow<Ring?> = taskDao.observeReminderInputs()
        .map { nextRing(it, clock.millis(), clock.zone) }

    /**
     * Moves this task's reminder to [untilMillis], or clears the snooze when it is null. It never
     * touches the task's own due date or time: snooze moves the ring, and the task stays due when
     * it was due and goes overdue normally.
     */
    suspend fun snooze(id: Long, untilMillis: Long?) = taskDao.setSnooze(id, untilMillis, now())

    /** Matches title or notes, ignoring case and diacritics; [query] is treated as plain text. */
    fun search(query: String): Flow<List<TaskWithList>> {
        val needle = normalise(query).trim()
        if (needle.isEmpty()) return flowOf(emptyList())
        val escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return taskDao.search("%$escaped%")
    }

    /**
     * Open top-level tasks per list. Lists with no open tasks are absent from the map, so callers
     * must default to 0. Subtasks are not counted, matching what [observeInList] shows.
     */
    fun observeOpenCounts(): Flow<Map<Long, Int>> =
        taskDao.observeOpenCounts().map { rows -> rows.associate { it.listId to it.openCount } }

    /** Appends after the last sibling in the same list, heading and parent. */
    suspend fun add(
        title: String,
        listId: Long,
        dueDate: LocalDate?,
        dueMinute: Int?,
        priority: Int,
        parentTaskId: Long? = null,
        headingId: Long? = null,
        recurrence: String? = null,
    ): Long = taskDao.insert(
        TaskEntity(
            title = title,
            titleNormalised = normalise(title),
            notesNormalised = "",
            listId = listId,
            headingId = headingId,
            parentTaskId = parentTaskId,
            dueDate = dueDate?.toEpochDay(),
            dueMinute = dueMinute,
            priority = priority,
            recurrence = recurrence,
            position = taskDao.nextPosition(listId, headingId, parentTaskId),
            updatedAt = now(),
        ),
    )

    /**
     * Writes only the editable columns of a live task; a stale or deleted snapshot changes nothing else.
     * A parent's live subtasks follow it to its list and heading.
     */
    suspend fun update(task: TaskEntity) = taskDao.updateEditableAndFollow(
        id = task.id,
        title = task.title,
        titleNormalised = normalise(task.title),
        notes = task.notes,
        notesNormalised = normalise(task.notes),
        listId = task.listId,
        headingId = task.headingId,
        dueDate = task.dueDate,
        dueMinute = task.dueMinute,
        priority = task.priority,
        recurrence = task.recurrence,
        now = now(),
    )

    /**
     * The id of the occurrence the last completion created, against the task that was completed.
     * Held in memory rather than in the database because the only thing that needs it is the undo
     * offered by the snackbar on screen, which does not outlive the process either. A completion
     * from the notification's Done button offers no undo and so needs nothing remembered.
     */
    private var lastRoll: Pair<Long, Long>? = null

    /**
     * Completing also completes open subtasks; reopening touches only the task itself.
     *
     * **A repeating task creates its next occurrence here**, which is why every screen and the
     * notification's Done button get that behaviour without knowing about it: this is the one door
     * every completion in the app goes through. Returns the new occurrence's id, or null when
     * nothing was created — the task does not repeat, it is being reopened, or its rule cannot be
     * read, in which case it is completed like any other task rather than landing on a guessed day.
     */
    suspend fun setDone(id: Long, done: Boolean): Long? {
        if (!done) {
            taskDao.setDone(id, false, now())
            return null
        }
        val task = taskDao.getTask(id) ?: return null
        val next = task.recurrence
            ?.let { nextOccurrence(it, task.dueDate?.let(LocalDate::ofEpochDay), today()) }
        if (next == null) {
            taskDao.setDone(id, true, now())
            return null
        }
        return taskDao.completeAndCreateNext(id, next.toEpochDay(), now())
            .also { lastRoll = id to it }
    }

    /**
     * Undo for [setDone] with done = true: reopens the task and the subtasks completed with it (same
     * completedAt stamp), so a cascade completion is reversed in full. [setDone] with false still
     * reopens only the task itself.
     *
     * When that completion created the next occurrence of a repeat, this removes it outright: it
     * was never a task Olda saw, so leaving it behind would turn one undone tick into two tasks.
     */
    suspend fun undoCompletion(id: Long) {
        lastRoll?.takeIf { it.first == id }?.let { (_, created) ->
            taskDao.deleteHard(created)
            lastRoll = null
        }
        taskDao.undoCompletion(id, now())
    }

    /** Also deletes the live subtasks; [restore] brings back exactly those. */
    suspend fun softDelete(id: Long) = taskDao.softDelete(id, now())

    suspend fun restore(id: Long) = taskDao.restore(id, now())
}
