package cz.olda.tasks.data

import cz.olda.tasks.data.dao.SettingDao
import cz.olda.tasks.data.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The handful of choices the app remembers between runs.
 *
 * Kept in the database rather than in Android's own preferences: `data/` must stay free of
 * Android-only APIs for Phase 8, and a setting that arrives as a `Flow` drops straight into the flows the
 * screens already build, so flipping a switch re-queries the day with no extra plumbing.
 *
 * Values are stored as text. Anything that cannot be read as a boolean — a value a later phase
 * wrote in another shape, or a half-finished write — reads as the caller's default rather than
 * throwing on a screen that was only asking whether a switch is on.
 */
class SettingsRepository(private val settingDao: SettingDao) {

    fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        settingDao.observe(key).map { it?.toBooleanStrictOrNull() ?: default }

    suspend fun setBoolean(key: String, value: Boolean) =
        settingDao.put(SettingEntity(key, value.toString()))

    companion object {
        /** Whether Today and Upcoming hide repeating tasks; one choice, shared by both screens. */
        const val HIDE_REPEATS = "hideRepeats"

        /**
         * Whether the app has already asked for permission to post notifications. Android stops
         * showing that dialog itself after two refusals, so "asked once" has to be our own memory.
         */
        const val NOTIFICATIONS_ASKED = "notificationsAsked"
    }
}
