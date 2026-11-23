package cz.olda.tasks

import android.app.Application

class TasksApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
