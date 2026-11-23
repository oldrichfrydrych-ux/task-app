package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cz.olda.tasks.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GroupDao {
    @Query("SELECT * FROM `groups` ORDER BY position, id")
    abstract fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM `groups`")
    abstract suspend fun nextPosition(): Int

    @Insert
    abstract suspend fun insert(group: GroupEntity): Long

    @Query("UPDATE `groups` SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE lists SET groupId = NULL WHERE groupId = :id")
    abstract suspend fun clearGroupOnLists(id: Long)

    @Query("DELETE FROM `groups` WHERE id = :id")
    abstract suspend fun deleteRow(id: Long)

    @Transaction
    open suspend fun create(name: String): Long = insert(GroupEntity(name = name, position = nextPosition()))

    @Transaction
    open suspend fun deleteKeepingLists(id: Long) {
        clearGroupOnLists(id)
        deleteRow(id)
    }
}
