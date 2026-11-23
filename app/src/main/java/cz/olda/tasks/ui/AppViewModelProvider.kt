package cz.olda.tasks.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import cz.olda.tasks.AppContainer
import cz.olda.tasks.TasksApplication
import cz.olda.tasks.ui.inbox.InboxViewModel
import cz.olda.tasks.ui.today.TodayViewModel
import cz.olda.tasks.ui.upcoming.UpcomingViewModel

/**
 * Builds every ViewModel. A new one is two lines inside [Factory]:
 * `initializer { XViewModel(container().listRepository) }`.
 */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { TodayViewModel(container().taskRepository, container().clock) }
        initializer { UpcomingViewModel(container().taskRepository) }
        initializer { InboxViewModel(container().taskRepository, container().listRepository) }
    }
}

/** The app's dependencies, reached through the creation extras instead of a global. */
fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as Application as TasksApplication).container
