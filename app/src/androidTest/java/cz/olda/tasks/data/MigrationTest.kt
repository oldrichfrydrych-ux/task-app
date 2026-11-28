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

    /**
     * The same guard one version on. Version 2 is what is on Olda's phone, so this is the run that
     * stands between `MIGRATION_2_3` and his 31 tasks: the task must come through with its repeat
     * intact, carrying no snooze, and the new `settings` table must arrive empty.
     */
    @Test
    fun aTaskWrittenAtVersionTwoSurvivesWithNoSnooze() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO lists (name, colorHex, position, isInbox, updatedAt) " +
                    "VALUES ('🌊Práce', '#3D6BE0', 0, 1, 0)",
            )
            db.execSQL(
                "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, " +
                    "priority, done, position, dueDate, dueMinute, recurrence, updatedAt) " +
                    "VALUES ('Číst při Týdenním resetu (pondělí).', '', " +
                    "'cist pri tydennim resetu (pondeli).', '', 1, 2, 0, 0, 20785, 900, " +
                    "'FREQ=WEEKLY;BYDAY=MO', 7)",
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
            db.query("SELECT title, dueDate, dueMinute, recurrence, snoozedUntil FROM tasks").use { row ->
                assertTrue("the task is gone", row.moveToFirst())
                assertEquals("Číst při Týdenním resetu (pondělí).", row.getString(0))
                assertEquals(20785L, row.getLong(1))
                assertEquals(900, row.getInt(2))
                assertEquals("FREQ=WEEKLY;BYDAY=MO", row.getString(3))
                assertTrue("a migrated task must have no snooze", row.isNull(4))
                assertEquals("exactly one task", 1, row.count)
            }
            db.query("SELECT COUNT(*) FROM settings").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals("the settings table starts empty", 0, row.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
