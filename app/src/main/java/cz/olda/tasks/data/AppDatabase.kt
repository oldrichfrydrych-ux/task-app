package cz.olda.tasks.data

import androidx.room.Database
import androidx.room.RoomDatabase
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.TagEntity
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.data.entity.TaskTagCrossRef

@Database(
    entities = [
        GroupEntity::class,
        ListEntity::class,
        HeadingEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        TaskEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase()
