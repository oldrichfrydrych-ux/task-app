package cz.olda.tasks

import android.content.Context
import androidx.room.Room
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import java.time.Clock

/** Hand-wired dependencies, one instance for the life of the process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "tasks.db")
            .addCallback(AppDatabase.seedInbox(appContext.getString(R.string.inbox)))
            .build()
    }

    val listRepository: ListRepository by lazy {
        ListRepository(database.listDao(), database.groupDao(), database.headingDao())
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), Clock.systemDefaultZone())
    }
}
