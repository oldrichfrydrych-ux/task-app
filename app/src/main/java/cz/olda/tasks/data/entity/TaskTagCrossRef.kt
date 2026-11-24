package cz.olda.tasks.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "task_tags", primaryKeys = ["taskId", "tagId"], indices = [Index("tagId")])
data class TaskTagCrossRef(
    val taskId: Long,
    val tagId: Long,
)
