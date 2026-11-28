package cz.olda.tasks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What Settings needs to know about reminders.
 *
 * [remindersBlocked] is deliberately false until the question has been asked: a fresh install must
 * not explain a refusal it has not asked about yet.
 */
data class SettingsState(
    val asked: Boolean = false,
    val notificationsEnabled: Boolean = true,
) {
    val remindersBlocked: Boolean get() = asked && !notificationsEnabled
}

/**
 * Owns the one-off notification question and the row that explains a refusal.
 *
 * [notificationsEnabled] is a function rather than a value because Olda can change Android's own
 * switch while the app is in the background; [refresh] re-reads it, and the screen asks for that
 * each time it appears.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val notificationsEnabled: () -> Boolean,
) : ViewModel() {

    private val enabled = MutableStateFlow(notificationsEnabled())

    val state: StateFlow<SettingsState> = combine(
        settings.observeBoolean(SettingsRepository.NOTIFICATIONS_ASKED, default = false),
        enabled,
    ) { asked, isEnabled -> SettingsState(asked, isEnabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    /** Remembers that the question has been put, whatever the answer was. */
    fun markAsked() {
        viewModelScope.launch { settings.setBoolean(SettingsRepository.NOTIFICATIONS_ASKED, true) }
        refresh()
    }

    fun refresh() {
        enabled.value = notificationsEnabled()
    }
}
