package cz.olda.tasks.data

import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.TaskEntity
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

    fun observeInList(listId: Long): Flow<List<TaskWithList>> = taskDao.observeInList(listId)

    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>> = taskDao.observeSubtasks(parentId)

    fun observeTask(id: Long): Flow<TaskEntity?> = taskDao.observeTask(id)

    /** The distinct list colours with an open task on each day in [from]..[to] inclusive. */
    fun observeDots(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<String>>> =
        taskDao.observeDots(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.groupBy({ LocalDate.ofEpochDay(it.dueDate) }, { it.colorHex })
        }

    /** Matches title or notes, ignoring case and diacritics; [query] is treated as plain text. */
    fun search(query: String): Flow<List<TaskWithList>> {
        val needle = normalise(query).trim()
        if (needle.isEmpty()) return flowOf(emptyList())
        val escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return taskDao.search("%$escaped%")
    }

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
            position = taskDao.nextPosition(listId, headingId, parentTaskId),
            updatedAt = now(),
        ),
    )

    suspend fun update(task: TaskEntity) = taskDao.update(
        task.copy(
            titleNormalised = normalise(task.title),
            notesNormalised = normalise(task.notes),
            updatedAt = now(),
        ),
    )

    /** Completing also completes open subtasks; reopening touches only the task itself. */
    suspend fun setDone(id: Long, done: Boolean) = taskDao.setDone(id, done, now())

    suspend fun softDelete(id: Long) = taskDao.setDeletedAt(id, now(), now())

    suspend fun restore(id: Long) = taskDao.setDeletedAt(id, null, now())
}
