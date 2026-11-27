package cz.olda.tasks.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The test that stands between a migration and Olda's 31 real tasks. It runs version 1 from the
 * committed `app/schemas/cz.olda.tasks.data.AppDatabase/1.json`, writes a task the way Phase 1
 * wrote them, migrates, and checks the task came through with everything it had.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun aTaskWrittenAtVersionOneSurvivesWithNoRepeat() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO lists (name, colorHex, position, isInbox, updatedAt) " +
                    "VALUES ('🌊Práce', '#3D6BE0', 0, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, " +
                    "priority, done, position, dueDate, dueMinute, updatedAt) " +
                    "VALUES ('Koupit zimní kabát', '', 'koupit zimni kabat', '', 1, 2, 0, 0, 20785, 900, 7)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            db.query(
                "SELECT title, notes, titleNormalised, listId, dueDate, dueMinute, priority, " +
                    "done, position, updatedAt, recurrence FROM tasks",
            ).use { row ->
                assertTrue("the task is gone", row.moveToFirst())
                assertEquals("Koupit zimní kabát", row.getString(0))
                assertEquals("", row.getString(1))
                assertEquals("koupit zimni kabat", row.getString(2))
                assertEquals(1L, row.getLong(3))
                assertEquals(20785L, row.getLong(4))
                assertEquals(900, row.getInt(5))
                assertEquals(2, row.getInt(6))
                assertEquals(0, row.getInt(7))
                assertEquals(0, row.getInt(8))
                assertEquals(7L, row.getLong(9))
                assertTrue("a migrated task must have no repeat", row.isNull(10))
                assertEquals("exactly one task", 1, row.count)
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
