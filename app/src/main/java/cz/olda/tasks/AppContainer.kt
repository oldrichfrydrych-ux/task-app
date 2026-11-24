package cz.olda.tasks

import android.content.Context
import androidx.room.Room
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import java.time.Clock

/** Hand-wired dependencies, one instance for the life of the process. */
class AppContainer(context: Context, dbName: String = "tasks.db") {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.seedInbox(appContext.getString(R.string.inbox)))
            .build()
    }

    val listRepository: ListRepository by lazy {
        ListRepository(database.listDao(), database.groupDao(), database.headingDao(), clock)
    }

    /** One clock for everything that reads "today", so the repository and the ViewModels agree. */
    val clock: Clock = Clock.systemDefaultZone()

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), clock)
    }
}
