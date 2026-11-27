package cz.olda.tasks.data

import cz.olda.tasks.data.dao.TagDao
import cz.olda.tasks.data.entity.TagEntity
import cz.olda.tasks.data.entity.TaskTagCrossRef
import cz.olda.tasks.data.parser.fold
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex
import kotlinx.coroutines.flow.Flow

/** A new tag takes Slate, as docs/design.md says under Colours. */
private val slate = ListPalette.single { it.name == "Slate" }.light.toHex()

/**
 * Tags, which Phase 2 is the first thing to create. Olda's call on 27 Nov: **a list has to exist
 * already, but a tag is made on the spot** — he has none yet, and tags are throwaway by nature.
 *
 * Matching is done in memory over every tag, folded, so `@Telefon` and `@telefón` find the same
 * one. `tags` has no unique index on `name` and adding one would be a second schema change, so
 * the de-duplicating lives here; the quick-add save already runs under a mutex, so there is one
 * writer at a time.
 */
class TagRepository(private val tagDao: TagDao) {

    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeTagsOf(taskId: Long): Flow<List<TagEntity>> = tagDao.observeOfTask(taskId)

    /**
     * Attaches each name to the task, creating any tag that does not exist yet. A name repeated
     * in one call attaches once, and attaching one the task already has changes nothing.
     */
    suspend fun attach(taskId: Long, names: List<String>) {
        if (names.isEmpty()) return
        val existing = tagDao.all().associateBy { fold(it.name) }.toMutableMap()
        names.map(::fold).filter { it.isNotBlank() }.distinct().forEach { folded ->
            val tag = existing[folded] ?: TagEntity(
                id = tagDao.insert(TagEntity(name = folded, colorHex = slate)),
                name = folded,
                colorHex = slate,
            ).also { existing[folded] = it }
            tagDao.link(TaskTagCrossRef(taskId = taskId, tagId = tag.id))
        }
    }

    suspend fun detach(taskId: Long, tagId: Long) = tagDao.unlink(taskId, tagId)
}
