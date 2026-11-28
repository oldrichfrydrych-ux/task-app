package cz.olda.tasks

import android.app.Application
import cz.olda.tasks.platform.Notifications

class TasksApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Creating a channel twice is a no-op, so this needs no "has it run yet" state.
        Notifications.ensureChannel(this)
    }
}
