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
