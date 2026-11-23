package cz.olda.tasks.data

import cz.olda.tasks.data.dao.GroupDao
import cz.olda.tasks.data.dao.HeadingDao
import cz.olda.tasks.data.dao.ListDao
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.ui.theme.parseHex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A group and its lists; a null [group] holds the ungrouped lists. */
data class GroupWithLists(val group: GroupEntity?, val lists: List<ListEntity>)

class ListRepository(
    private val listDao: ListDao,
    private val groupDao: GroupDao,
    private val headingDao: HeadingDao,
) {
    fun observeGroupsWithLists(): Flow<List<GroupWithLists>> =
        combine(groupDao.observeAll(), listDao.observeAll()) { groups, lists ->
            val ungrouped = lists.filter { l -> l.groupId == null || groups.none { it.id == l.groupId } }
            buildList {
                if (ungrouped.isNotEmpty()) add(GroupWithLists(null, ungrouped))
                groups.forEach { g -> add(GroupWithLists(g, lists.filter { it.groupId == g.id })) }
            }
        }

    fun observeLists(): Flow<List<ListEntity>> = listDao.observeAll()

    suspend fun inboxId(): Long = listDao.inboxId()

    /** A null [colorHex] takes the next preset colour; a non-null one is stored as given. */
    suspend fun createList(name: String, colorHex: String? = null, groupId: Long? = null): Long =
        listDao.create(name, colorHex, groupId)

    suspend fun renameList(id: Long, name: String) = listDao.rename(id, name)

    suspend fun recolourList(id: Long, colorHex: String) {
        require(parseHex(colorHex) != null) { "Not a #RRGGBB colour: $colorHex" }
        listDao.recolour(id, colorHex)
    }

    /** Moves the list's tasks to Inbox first; the Inbox itself is never deleted. */
    suspend fun deleteList(id: Long) = listDao.deleteAndMoveTasksToInbox(id)

    suspend fun createGroup(name: String): Long = groupDao.create(name)

    suspend fun renameGroup(id: Long, name: String) = groupDao.rename(id, name)

    suspend fun deleteGroup(id: Long) = groupDao.deleteKeepingLists(id)

    fun observeHeadings(listId: Long): Flow<List<HeadingEntity>> = headingDao.observeForList(listId)

    suspend fun createHeading(listId: Long, name: String): Long = headingDao.create(listId, name)

    suspend fun renameHeading(id: Long, name: String) = headingDao.rename(id, name)

    suspend fun deleteHeading(id: Long) = headingDao.deleteKeepingTasks(id)
}
