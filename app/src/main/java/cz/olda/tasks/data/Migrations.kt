package cz.olda.tasks.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 → 2: one optional column for a repeat.
 *
 * Additive, so every task keeps everything it has and comes out with no repeat. There is no
 * destructive-migration fallback anywhere in this app and there must never be one: Olda's real
 * tasks live only on his phone. `MigrationTest` runs this against the committed version 1 schema
 * before it is ever allowed near them.
 *
 * `androidx.room` and `androidx.sqlite` are not `android.*`, so `data/` stays free of
 * platform-only APIs for Phase 8 — `DataLayerIsPlatformFreeTest` holds that line.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT DEFAULT NULL")
    }
}

/**
 * Version 2 → 3: one optional column for a snooze, and a table for the choices the app remembers.
 *
 * Additive again, for the same reason: version 2 is what is on Olda's phone with his 31 tasks in
 * it, and `MigrationTest` runs this against the committed version 2 schema before it is allowed
 * near them. Every task comes out with no snooze, and `settings` arrives empty, so the first read
 * of a flag gets its caller's default.
 *
 * **The `CREATE TABLE` statement is copied verbatim from the `createSql` of the `settings` entity
 * in the exported `schemas/cz.olda.tasks.data.AppDatabase/3.json`**, with `${TABLE_NAME}` filled
 * in. Room validates the schema it opens against the one it generated, column by column, so a
 * hand-written variant — a different order, a missing `NOT NULL`, `IF NOT EXISTS` left off — fails
 * at open time rather than here, with a message that does not say which difference it minded.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER DEFAULT NULL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `settings` " +
                "(`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
        )
    }
}
