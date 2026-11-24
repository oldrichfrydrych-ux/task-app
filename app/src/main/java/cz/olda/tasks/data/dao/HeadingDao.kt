package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cz.olda.tasks.data.entity.HeadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HeadingDao {
    @Query("SELECT * FROM headings WHERE listId = :listId ORDER BY position, id")
    abstract fun observeForList(listId: Long): Flow<List<HeadingEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM headings WHERE listId = :listId")
    abstract suspend fun nextPosition(listId: Long): Int

    @Insert
    abstract suspend fun insert(heading: HeadingEntity): Long

    @Query("UPDATE headings SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE tasks SET headingId = NULL WHERE headingId = :id")
    abstract suspend fun clearHeadingOnTasks(id: Long)

    @Query("DELETE FROM headings WHERE id = :id")
    abstract suspend fun deleteRow(id: Long)

    @Transaction
    open suspend fun create(listId: Long, name: String): Long =
        insert(HeadingEntity(listId = listId, name = name, position = nextPosition(listId)))

    @Transaction
    open suspend fun deleteKeepingTasks(id: Long) {
        clearHeadingOnTasks(id)
        deleteRow(id)
    }
}
