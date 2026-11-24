package cz.olda.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun close() { db.close() }

    @Test fun opensAtVersionOneWithEveryTable() {
        val tables = db.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        listOf("groups", "lists", "headings", "tags", "task_tags", "tasks")
            .forEach { assertTrue("$it missing", tables.contains(it)) }
    }

    /** [sql] inserts one row with `'%s'` as the sync id; NULL ids may repeat, a repeated real one may not. */
    private fun assertSyncIdUnique(sql: String) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(sql.format("'g1'"))
        sqlite.execSQL(sql.format("NULL"))
        sqlite.execSQL(sql.format("NULL"))
        sqlite.execSQL(sql.format("'g2'"))
        try {
            sqlite.execSQL(sql.format("'g1'"))
            org.junit.Assert.fail("duplicate sync id accepted")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
        }
    }

    @Test fun googleListIdIsUnique() = assertSyncIdUnique(
        "INSERT INTO lists (name, colorHex, position, isInbox, updatedAt, googleListId) VALUES ('n', '#000000', 0, 0, 0, %s)",
    )

    @Test fun googleIdIsUnique() = assertSyncIdUnique(
        "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, priority, done, position, updatedAt, googleId) " +
            "VALUES ('t', '', 't', '', 1, 4, 0, 0, 0, %s)",
    )
}
