package cz.olda.tasks.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One remembered choice, stored as text.
 *
 * The app has a handful of these — whether repeats are hidden on Today and Upcoming, whether the
 * notification question has already been asked — and they live in the database rather than in
 * Android's own preferences for two reasons: `data/` stays free of Android-only APIs for Phase 8,
 * which moves this package to Windows, and a change arrives as a `Flow` that the screens already know
 * how to combine into the flows they build.
 *
 * The value is text and not a typed column because there are two kinds of setting so far and both
 * are booleans; [cz.olda.tasks.data.SettingsRepository] is the only thing that reads or writes the
 * encoding, and it treats anything it cannot read as the caller's default rather than crashing a
 * screen.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
