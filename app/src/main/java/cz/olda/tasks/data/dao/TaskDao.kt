package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cz.olda.tasks.data.TaskWithList
import cz.olda.tasks.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/** One list's open-task count; the row type behind [TaskDao.observeOpenCounts]. */
data class ListOpenCount(val listId: Long, val openCount: Int)

/** One list colour that has an open task on [dueDate]; the row type behind [TaskDao.observeDots]. */
data class DayDot(val dueDate: Long, val colorHex: String)

/**
 * Every read query excludes soft-deleted rows (deletedAt IS NULL), with two deliberate exceptions:
 * [nextPosition] counts deleted siblings too, so a restored task never collides with a live one, and
 * [deletedAtOf] must see the stamp of a deleted row. Writes are guarded individually (see each one).
 * Day queries (overdue, a day, a range, dots), the list, search and count queries return open tasks
 * only; subtask and single-task queries also return done ones.
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

    /** Open top-level tasks of a list. */
    @Query(
        "SELECT tasks.*, lists.name AS listName, lists.colorHex AS listColorHex FROM tasks " +
            "JOIN lists ON lists.id = tasks.listId " +
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 AND tasks.parentTaskId IS NULL AND tasks.listId = :listId " +
            "ORDER BY tasks.position, tasks.id",
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
            "WHERE tasks.deletedAt IS NULL AND tasks.done = 0 " +
            "AND (tasks.titleNormalised LIKE :pattern ESCAPE '\\' OR tasks.notesNormalised LIKE :pattern ESCAPE '\\') " +
            "ORDER BY tasks.id",
    )
    abstract fun search(pattern: String): Flow<List<TaskWithList>>

    @Query(
        "SELECT listId, COUNT(*) AS openCount FROM tasks " +
            "WHERE deletedAt IS NULL AND done = 0 AND parentTaskId IS NULL GROUP BY listId",
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

    /**
     * Writes only the editable columns, and only to a live row: a stale snapshot can neither
     * revert done, completedAt, position or deletedAt nor edit a soft-deleted task.
     */
    @Query(
        "UPDATE tasks SET title = :title, titleNormalised = :titleNormalised, notes = :notes, " +
            "notesNormalised = :notesNormalised, listId = :listId, headingId = :headingId, dueDate = :dueDate, " +
            "dueMinute = :dueMinute, priority = :priority, updatedAt = :now WHERE id = :id AND deletedAt IS NULL",
    )
    abstract suspend fun updateEditable(
        id: Long,
        title: String,
        titleNormalised: String,
        notes: String,
        notesNormalised: String,
        listId: Long,
        headingId: Long?,
        dueDate: Long?,
        dueMinute: Int?,
        priority: Int,
        now: Long,
    )

    /** Puts every live subtask of [parentId] in the list and heading the parent now has. */
    @Query(
        "UPDATE tasks SET " +
            "listId = (SELECT p.listId FROM tasks p WHERE p.id = :parentId), " +
            "headingId = (SELECT p.headingId FROM tasks p WHERE p.id = :parentId), updatedAt = :now " +
            "WHERE parentTaskId = :parentId AND deletedAt IS NULL",
    )
    abstract suspend fun followParent(parentId: Long, now: Long)

    /** [updateEditable], then the live subtasks follow the task: they always sit where their parent sits. */
    @Transaction
    open suspend fun updateEditableAndFollow(
        id: Long,
        title: String,
        titleNormalised: String,
        notes: String,
        notesNormalised: String,
        listId: Long,
        headingId: Long?,
        dueDate: Long?,
        dueMinute: Int?,
        priority: Int,
        now: Long,
    ) {
        updateEditable(id, title, titleNormalised, notes, notesNormalised, listId, headingId, dueDate, dueMinute, priority, now)
        followParent(id, now)
    }

    /** A no-op when the task is already in the requested state, so completedAt keeps its first value. */
    @Query(
        "UPDATE tasks SET done = :done, completedAt = :completedAt, updatedAt = :now WHERE id = :id AND done != :done",
    )
    abstract suspend fun markDone(id: Long, done: Boolean, completedAt: Long?, now: Long)

    @Query("SELECT completedAt FROM tasks WHERE id = :id")
    abstract suspend fun completedAtOf(id: Long): Long?

    /** Reopens the task and the live subtasks completed with it (same completedAt stamp). */
    @Query(
        "UPDATE tasks SET done = 0, completedAt = NULL, updatedAt = :now " +
            "WHERE (id = :id OR parentTaskId = :id) AND done = 1 AND completedAt = :stamp AND deletedAt IS NULL",
    )
    abstract suspend fun reopenStamped(id: Long, stamp: Long, now: Long)

    /** Undoes a completion, cascade included. A fixed clock cannot tell a subtask completed earlier apart. */
    @Transaction
    open suspend fun undoCompletion(id: Long, now: Long) {
        val stamp = completedAtOf(id) ?: return
        reopenStamped(id, stamp, now)
    }

    /** Only live, still-open subtasks: ones already done keep their original completion time. */
    @Query(
        "UPDATE tasks SET done = 1, completedAt = :now, updatedAt = :now " +
            "WHERE parentTaskId = :parentId AND done = 0 AND deletedAt IS NULL",
    )
    abstract suspend fun completeOpenSubtasks(parentId: Long, now: Long)

    @Query("SELECT deletedAt FROM tasks WHERE id = :id")
    abstract suspend fun deletedAtOf(id: Long): Long?

    /** Stamps the task and its live subtasks with one shared [stamp]. */
    @Query("UPDATE tasks SET deletedAt = :stamp, updatedAt = :stamp WHERE (id = :id OR parentTaskId = :id) AND deletedAt IS NULL")
    abstract suspend fun stampDeleted(id: Long, stamp: Long)

    /** Clears only rows carrying [stamp], so subtasks deleted on their own earlier stay deleted. */
    @Query("UPDATE tasks SET deletedAt = NULL, updatedAt = :now WHERE (id = :id OR parentTaskId = :id) AND deletedAt = :stamp")
    abstract suspend fun clearDeleted(id: Long, stamp: Long, now: Long)

    /**
     * Deleting a parent deletes its live subtasks with the same timestamp. That shared stamp is the only
     * link [restore] has: a subtask deleted on its own in the same millisecond as its parent would be
     * revived with it. A real clock makes that effectively impossible; Phase 4 revisits it for sync.
     */
    @Transaction
    open suspend fun softDelete(id: Long, now: Long) = stampDeleted(id, now)

    /** Undoes [softDelete]: clears the task and every subtask carrying the same stamp (see [softDelete]). */
    @Transaction
    open suspend fun restore(id: Long, now: Long) {
        val stamp = deletedAtOf(id) ?: return
        clearDeleted(id, stamp, now)
    }

    /** Completing also completes open subtasks; reopening touches only the task itself. */
    @Transaction
    open suspend fun setDone(id: Long, done: Boolean, now: Long) {
        markDone(id, done, if (done) now else null, now)
        if (done) completeOpenSubtasks(id, now)
    }
}
