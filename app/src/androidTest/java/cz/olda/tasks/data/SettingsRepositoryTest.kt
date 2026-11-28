package cz.olda.tasks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.data.entity.SettingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The choices the app remembers. Room's own flows need a real database, so this runs on the phone
 * against an in-memory one, the way `TaskRepositoryTest` does.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SettingsRepository

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = SettingsRepository(db.settingDao())
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun anAbsentFlagReadsAsItsDefault() = runBlocking {
        assertFalse(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
        assertTrue(repo.observeBoolean("whatever", default = true).first())
    }

    @Test
    fun aFlagIsRememberedAndCanBeChangedBack() = runBlocking {
        repo.setBoolean(SettingsRepository.HIDE_REPEATS, true)
        assertTrue(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
        repo.setBoolean(SettingsRepository.HIDE_REPEATS, false)
        assertFalse(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
    }

    @Test
    fun twoFlagsDoNotCollide() = runBlocking {
        repo.setBoolean(SettingsRepository.HIDE_REPEATS, true)
        assertFalse(repo.observeBoolean(SettingsRepository.NOTIFICATIONS_ASKED, default = false).first())
    }

    /** A value a later phase wrote in another shape must not be able to break a screen. */
    @Test
    fun anUnreadableValueReadsAsTheDefault() = runBlocking {
        db.settingDao().put(SettingEntity(SettingsRepository.HIDE_REPEATS, "perhaps"))
        assertTrue(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = true).first())
        assertFalse(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
    }
}
