package cz.olda.tasks.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.olda.tasks.data.dao.GroupDao
import cz.olda.tasks.data.dao.HeadingDao
import cz.olda.tasks.data.dao.ListDao
import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.TagEntity
import cz.olda.tasks.data.entity.TaskEntity
import cz.olda.tasks.data.entity.TaskTagCrossRef
import cz.olda.tasks.ui.theme.ListPalette
import cz.olda.tasks.ui.theme.toHex

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
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun groupDao(): GroupDao
    abstract fun headingDao(): HeadingDao
    abstract fun taskDao(): TaskDao

    companion object {
        /** Seeds the undeletable Inbox list when the database is first created. */
        fun seedInbox(inboxName: String): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.insert(
                    "lists",
                    SQLiteDatabase.CONFLICT_NONE,
                    ContentValues().apply {
                        put("name", inboxName)
                        put("colorHex", ListPalette[0].light.toHex())
                        put("position", 0)
                        put("isInbox", 1)
                        put("updatedAt", 0)
                    },
                )
            }
        }
    }
}
