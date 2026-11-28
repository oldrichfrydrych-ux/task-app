package cz.olda.tasks.data

import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.data.recurrence.nextOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** The sections of one day; [overdue] and [tomorrow] are empty unless the day is today. */
data class DaySections(
    val overdue: List<TaskWithList>,
    val timed: List<TaskWithList>,
    val anytime: List<TaskWithList>,
    val tomorrow: List<TaskWithList>,
)

data class UpcomingDay(val date: LocalDate, val tasks: List<TaskWithList>)

class TaskRepository(
    private val taskDao: TaskDao,
    private val clock: Clock,
) {
    private fun today(): LocalDate = LocalDate.now(clock)

    private fun now(): Long = clock.millis()

    /** "Today" is read when the flow is collected, not when it is created. */
    fun observeDay(day: LocalDate): Flow<DaySections> = flow {
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
                DaySections(
                    overdue = o,
                    timed = d.filter { it.task.dueMinute != null },
                    anytime = d.filter { it.task.dueMinute == null },
                    tomorrow = t,
                )
            },
        )
    }

    /** Open tasks due after today, grouped by day, earliest first. */
    fun observeUpcoming(): Flow<List<UpcomingDay>> = flow {
        emitAll(
            taskDao.observeDueAfter(today().toEpochDay()).map { rows ->
                rows.groupBy { it.task.dueDate!! }
                    .map { (epochDay, tasks) -> UpcomingDay(LocalDate.ofEpochDay(epochDay), tasks) }
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

    /** The distinct list colours with an open task (subtasks included) on each day in [from]..[to] inclusive. */
    fun observeDots(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<String>>> =
        taskDao.observeDots(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.groupBy({ LocalDate.ofEpochDay(it.dueDate) }, { it.colorHex })
        }

    /** How many of [day]'s tasks are already done; the open ones come from [observeDay]. */
    fun observeDoneCount(day: LocalDate): Flow<Int> = taskDao.observeDoneCount(day.toEpochDay())

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
