package cz.olda.tasks

import android.content.Context
import androidx.room.Room
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.MIGRATION_1_2
import cz.olda.tasks.data.MIGRATION_2_3
import cz.olda.tasks.data.SettingsRepository
import cz.olda.tasks.data.TagRepository
import cz.olda.tasks.data.liveZoneClock
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.platform.ReminderScheduler
import cz.olda.tasks.platform.ReminderSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/** Hand-wired dependencies, one instance for the life of the process. */
class AppContainer(context: Context, dbName: String = "tasks.db") {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.seedInbox(appContext.getString(R.string.inbox)))
            // Never fallbackToDestructiveMigration: Olda's tasks live only on this phone.
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    val listRepository: ListRepository by lazy {
        ListRepository(database.listDao(), database.groupDao(), database.headingDao(), clock)
    }

    /**
     * One clock for everything that reads "today", so the repository and the ViewModels agree.
     *
     * [liveZoneClock] and not `Clock.systemDefaultZone()`: that one captures the time zone when it
     * is made, which would leave a booked reminder on the old zone after a flight even though the
     * app re-arms on `TIMEZONE_CHANGED` for exactly that case.
     */
    val clock: Clock = liveZoneClock()

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), clock)
    }

    val tagRepository: TagRepository by lazy {
        TagRepository(database.tagDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.settingDao())
    }

    /**
     * Lives as long as the process, which is what [ReminderSync.start] collects in. Not a
     * ViewModel's scope: the alarm has to stay in step with the data whether a screen is on or not.
     */
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val reminderSync: ReminderSync by lazy {
        ReminderSync(appScope, taskRepository, ReminderScheduler(appContext))
    }
}
