package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cz.olda.tasks.data.entity.TagEntity
import cz.olda.tasks.data.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    abstract fun observeAll(): Flow<List<TagEntity>>

    @Query(
        "SELECT tags.* FROM tags JOIN task_tags ON task_tags.tagId = tags.id " +
            "WHERE task_tags.taskId = :taskId ORDER BY tags.name",
    )
    abstract fun observeOfTask(taskId: Long): Flow<List<TagEntity>>

    /** Every tag, for matching a typed name against in memory; there are only ever a handful. */
    @Query("SELECT * FROM tags")
    abstract suspend fun all(): List<TagEntity>

    @Insert
    abstract suspend fun insert(tag: TagEntity): Long

    /**
     * `task_tags` is keyed on (taskId, tagId), so attaching a tag a task already has would throw.
     * Ignoring the conflict is what makes "@telefon volat @telefon" save instead of failing.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun link(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    abstract suspend fun unlink(taskId: Long, tagId: Long)
}
