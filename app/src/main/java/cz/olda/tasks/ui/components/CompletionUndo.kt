package cz.olda.tasks.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import cz.olda.tasks.R
import cz.olda.tasks.ui.LocalSnackbarHostState
import kotlinx.coroutines.launch

/**
 * The single place "completed, with undo" lives: every screen that lists tasks completes one
 * through the returned function instead of writing to the repository itself.
 *
 * Calling it with a task id runs [onComplete] straight away and posts a "Task completed" snackbar
 * on [LocalSnackbarHostState]. If the snackbar's Undo is tapped, [onUndo] runs for the same id.
 * Back [onUndo] with `TaskRepository.undoCompletion`, not `setDone(id, false)`: completing a
 * parent also completes its subtasks, and only `undoCompletion` reopens them again.
 *
 * A new completion replaces the snackbar of the previous one, which is then final. The undo is
 * offered only while the calling screen is on screen.
 */
@Composable
fun rememberCompletionUndo(
    onComplete: (Long) -> Unit,
    onUndo: (Long) -> Unit,
): (Long) -> Unit {
    val host = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.task_completed)
    val undoLabel = stringResource(R.string.undo)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnUndo by rememberUpdatedState(onUndo)
    return remember(host, scope, message, undoLabel) {
        { id: Long ->
            currentOnComplete(id)
            scope.launch {
                host.currentSnackbarData?.dismiss()
                val result = host.showSnackbar(
                    message = message,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) currentOnUndo(id)
            }
            Unit
        }
    }
}
