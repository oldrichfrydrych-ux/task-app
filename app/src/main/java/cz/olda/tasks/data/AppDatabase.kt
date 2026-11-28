package cz.olda.tasks.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.olda.tasks.data.dao.GroupDao
import cz.olda.tasks.data.dao.HeadingDao
import cz.olda.tasks.data.dao.ListDao
import cz.olda.tasks.data.dao.SettingDao
import cz.olda.tasks.data.dao.TagDao
import cz.olda.tasks.data.dao.TaskDao
import cz.olda.tasks.data.entity.GroupEntity
import cz.olda.tasks.data.entity.HeadingEntity
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.entity.SettingEntity
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
        SettingEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun groupDao(): GroupDao
    abstract fun headingDao(): HeadingDao
    abstract fun taskDao(): TaskDao
    abstract fun tagDao(): TagDao
    abstract fun settingDao(): SettingDao

    companion object {
        /**
         * Seeds the undeletable Inbox list when the database is first created.
         *
         * Written as SQL rather than with `ContentValues`, which would pull
         * `android.content` into this package: CLAUDE.md keeps `data/` free of
         * Android-only APIs so Phase 8 can move it to a shared module unchanged.
         * `DataLayerIsPlatformFreeTest` holds that line, and
         * `AppContainerTest.theSeededInboxCarriesEveryColumnTheAppReliesOn` checks every
         * column this statement writes, since nothing type-checks hand-written SQL.
         */
        fun seedInbox(inboxName: String): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO lists (name, colorHex, position, isInbox, updatedAt) " +
                        "VALUES (?, ?, 0, 1, 0)",
                    arrayOf(inboxName, ListPalette[0].light.toHex()),
                )
            }
        }
    }
}
