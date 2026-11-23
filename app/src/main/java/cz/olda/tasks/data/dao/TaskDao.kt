package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** One list's open-task count; the row type behind [TaskDao.observeOpenCounts]. */
data class ListOpenCount(val listId: Long, val openCount: Int)

/** One list colour that has an open task on [dueDate]; the row type behind [TaskDao.observeDots]. */
data class DayDot(val dueDate: Long, val colorHex: String)

/**
 * Every query here excludes soft-deleted rows (deletedAt IS NULL). Day queries (overdue, a day,
 * a range, dots) return open tasks only; list, subtask, single-task and search queries also
 * return done ones.
 */
@Dao
abstract class TaskDao {
    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 AND tasks.dueDate < :epochDay " +
            "ORDER BY tasks.dueDate, tasks.dueMinute IS NULL, tasks.dueMinute, tasks.position, tasks.id",
    )
    abstract fun observeOverdue(epochDay: Long): Flow<List<TaskWithList>>

    /** Open tasks due from [fromDay] to [toDay] inclusive; timed ones first within a day. */
    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 AND tasks.dueDate BETWEEN :fromDay AND :toDay " +
            "ORDER BY tasks.dueDate, tasks.dueMinute IS NULL, tasks.dueMinute, tasks.position, tasks.id",
    )
    abstract fun observeDueBetween(fromDay: Long, toDay: Long): Flow<List<TaskWithList>>

    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 AND tasks.dueDate > :epochDay " +
            "ORDER BY tasks.dueDate, tasks.dueMinute IS NULL, tasks.dueMinute, tasks.position, tasks.id",
    )
    abstract fun observeDueAfter(epochDay: Long): Flow<List<TaskWithList>>

    /** Top-level tasks of a list, open ones first. */
    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.parentTaskId IS NULL AND tasks.listId = :listId " +
            "ORDER BY tasks.done, tasks.position, tasks.id",
    )
    abstract fun observeInList(listId: Long): Flow<List<TaskWithList>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND parentTaskId = :parentId ORDER BY position, id")
    abstract fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    abstract fun observeTask(id: Long): Flow<TaskEntity?>

    @Query(
        "SELECT DISTINCT tasks.dueDate AS dueDate, lists.colorHex AS colorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 AND tasks.dueDate BETWEEN :fromDay AND :toDay " +
            "ORDER BY tasks.dueDate, lists.colorHex",
    )
    abstract fun observeDots(fromDay: Long, toDay: Long): Flow<List<DayDot>>

    /** [pattern] is a LIKE pattern over already-normalised text, with backslash as the escape character. */
    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL " +
            "AND (tasks.titleNormalised LIKE :pattern ESCAPE '\\' OR tasks.notesNormalised LIKE :pattern ESCAPE '\\') " +
            "ORDER BY tasks.done, tasks.id",
    )
    abstract fun search(pattern: String): Flow<List<TaskWithList>>

    @Query(
        "SELECT listId, COUNT(*) AS openCount FROM tasks " +
            "WHERE deletedAt IS NULL AND done = 0 GROUP BY listId",
    )
    abstract fun observeOpenCounts(): Flow<List<ListOpenCount>>

    /** Next free position among the siblings that share list, heading and parent. */
    @Query(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM tasks " +
            "WHERE listId = :listId AND headingId IS :headingId AND parentTaskId IS :parentTaskId",
    )
    abstract suspend fun nextPosition(listId: Long, headingId: Long?, parentTaskId: Long?): Int

    @Insert
    abstract suspend fun insert(task: TaskEntity): Long

    @Update
    abstract suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET done = :done, completedAt = :completedAt, updatedAt = :now WHERE id = :id")
    abstract suspend fun markDone(id: Long, done: Boolean, completedAt: Long?, now: Long)

    /** Only live, still-open subtasks: ones already done keep their original completion time. */
    @Query(
        "UPDATE tasks SET done = 1, completedAt = :now, updatedAt = :now " +
            "WHERE parentTaskId = :parentId AND done = 0 AND deletedAt IS NULL",
    )
    abstract suspend fun completeOpenSubtasks(parentId: Long, now: Long)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :now WHERE id = :id")
    abstract suspend fun setDeletedAt(id: Long, deletedAt: Long?, now: Long)

    /** Completing also completes open subtasks; reopening touches only the task itself. */
    @Transaction
    open suspend fun setDone(id: Long, done: Boolean, now: Long) {
        markDone(id, done, if (done) now else null, now)
        if (done) completeOpenSubtasks(id, now)
    }
}
