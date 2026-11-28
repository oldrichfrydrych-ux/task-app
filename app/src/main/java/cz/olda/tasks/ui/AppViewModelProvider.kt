package cz.olda.tasks.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import cz.olda.tasks.AppContainer
import cz.olda.tasks.TasksApplication
import cz.olda.tasks.platform.notificationsEnabled
import cz.olda.tasks.ui.inbox.InboxViewModel
import cz.olda.tasks.ui.settings.SettingsViewModel
import cz.olda.tasks.ui.lists.ListDetailViewModel
import cz.olda.tasks.ui.lists.ListsViewModel
import cz.olda.tasks.ui.quickadd.QuickAddViewModel
import cz.olda.tasks.ui.search.SearchViewModel
import cz.olda.tasks.ui.task.TaskDetailViewModel
import cz.olda.tasks.ui.today.TodayViewModel
import cz.olda.tasks.ui.upcoming.UpcomingViewModel

/**
 * Builds every ViewModel. A new one is two lines inside [Factory]:
 * `initializer { XViewModel(container().listRepository) }`.
 */
object AppViewModelProvider {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            TodayViewModel(container().taskRepository, container().clock, container().settingsRepository)
        }
        initializer { UpcomingViewModel(container().taskRepository, container().settingsRepository) }
        // Saved state lets a half-typed task survive the process being killed.
        initializer {
            QuickAddViewModel(
                container().taskRepository,
                container().listRepository,
                container().tagRepository,
                container().clock,
                createSavedStateHandle(),
            )
        }
        initializer { InboxViewModel(container().taskRepository, container().listRepository) }
        initializer { SearchViewModel(container().taskRepository) }
        // The notification switch is Android's, not ours, so it is read when asked for.
        initializer {
            val application = this[APPLICATION_KEY] as Application
            SettingsViewModel(container().settingsRepository) { notificationsEnabled(application) }
        }
        initializer { ListsViewModel(container().listRepository, container().taskRepository) }
        // The list id is the route argument, which the nav back stack puts in the saved state.
        initializer {
            ListDetailViewModel(
                checkNotNull(createSavedStateHandle().get<Long>("listId")),
                container().listRepository,
                container().taskRepository,
            )
        }
        initializer {
            TaskDetailViewModel(
                checkNotNull(createSavedStateHandle().get<Long>("taskId")),
                container().taskRepository,
                container().listRepository,
                container().tagRepository,
            )
        }
    }
}

/** The app's dependencies, reached through the creation extras instead of a global. */
fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as Application as TasksApplication).container
