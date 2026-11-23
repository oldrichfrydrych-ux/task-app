package cz.olda.tasks.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [Index("listId"), Index("headingId"), Index("parentTaskId"), Index("dueDate")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val titleNormalised: String,
    val notesNormalised: String,
    val listId: Long,
    val headingId: Long? = null,
    val parentTaskId: Long? = null,
    /** Local epoch day, never an instant. */
    val dueDate: Long? = null,
    /** Minutes since local midnight. */
    val dueMinute: Int? = null,
    val priority: Int = 4,
    val done: Boolean = false,
    val completedAt: Long? = null,
    val position: Int = 0,
    val deletedAt: Long? = null,
    // Reserved for later phases so they need no migration; nothing writes these in Phase 1.
    val durationMinutes: Int? = null,
    val goalId: Long? = null,
    val googleId: String? = null,
    val updatedAt: Long = 0,
)
