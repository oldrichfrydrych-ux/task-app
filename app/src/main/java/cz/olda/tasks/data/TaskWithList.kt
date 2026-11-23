package cz.olda.tasks.data

import androidx.room.Embedded
import cz.olda.tasks.data.entity.TaskEntity

/** A task joined with the name and colour of the list it belongs to, for rows that show both. */
data class TaskWithList(
    @Embedded val task: TaskEntity,
    val listName: String,
    val listColorHex: String,
)
