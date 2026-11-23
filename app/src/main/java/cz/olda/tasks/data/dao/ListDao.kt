package cz.olda.tasks.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ListDao {
    @Query("SELECT * FROM lists ORDER BY isInbox DESC, position, id")
    abstract fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT id FROM lists WHERE isInbox = 1 LIMIT 1")
    abstract suspend fun inboxId(): Long

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM lists")
    abstract suspend fun nextPosition(): Int

    @Query("SELECT isInbox FROM lists WHERE id = :id")
    abstract suspend fun isInbox(id: Long): Boolean?

    @Insert
    abstract suspend fun insert(list: ListEntity): Long

    @Query("UPDATE lists SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE lists SET colorHex = :colorHex WHERE id = :id")
    abstract suspend fun recolour(id: Long, colorHex: String)

    /** Inbox never joins a group, so the guard is in the statement itself. */
    @Query("UPDATE lists SET groupId = :groupId WHERE id = :id AND isInbox = 0")
    abstract suspend fun setGroup(id: Long, groupId: Long?)

    /** Moves, never cascades: live tasks only; trashed ones are left alone. */
    @Query("UPDATE tasks SET listId = :toListId, headingId = NULL WHERE listId = :fromListId AND deletedAt IS NULL")
    abstract suspend fun moveTasks(fromListId: Long, toListId: Long)

    @Query("DELETE FROM headings WHERE listId = :listId")
    abstract suspend fun deleteHeadingsOf(listId: Long)

    @Query("DELETE FROM lists WHERE id = :id AND isInbox = 0")
    abstract suspend fun deleteRow(id: Long)

    /** A null [colorHex] takes the next preset, chosen by position; a non-null one is stored as given. */
    @Transaction
    open suspend fun create(name: String, colorHex: String?, groupId: Long?): Long {
        val position = nextPosition()
        val colour = colorHex ?: ListPalette[position % ListPalette.size].light.toHex()
        return insert(ListEntity(name = name, colorHex = colour, groupId = groupId, position = position))
    }

    @Transaction
    open suspend fun deleteAndMoveTasksToInbox(id: Long) {
        if (isInbox(id) != false) return
        moveTasks(id, inboxId())
        deleteHeadingsOf(id)
        deleteRow(id)
    }
}
