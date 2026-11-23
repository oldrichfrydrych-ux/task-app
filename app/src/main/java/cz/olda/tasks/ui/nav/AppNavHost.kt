package cz.olda.tasks.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cz.olda.tasks.R
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.olda.tasks.ui.AppViewModelProvider
import cz.olda.tasks.ui.Destination
import cz.olda.tasks.ui.inbox.InboxScreen
import cz.olda.tasks.ui.lists.ListsScreen
import cz.olda.tasks.ui.today.TodayScreen
import cz.olda.tasks.ui.upcoming.UpcomingScreen
import cz.olda.tasks.ui.screens.PlaceholderScreen

object Routes {
    const val LIST = "list/{listId}"
    const val TASK = "task/{taskId}"
    const val SEARCH = "search"

    fun list(id: Long) = "list/$id"
    fun task(id: Long) = "task/$id"
}

/**
 * The four top-level routes plus list, task and search. The graph always starts on
 * Today: a start that followed the restored tab would break popping to the start.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = Destination.Today.route, modifier = modifier) {
        composable(Destination.Today.route) {
            TodayScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenTask = { navController.navigate(Routes.task(it)) },
            )
        }
        composable(Destination.Upcoming.route) {
            UpcomingScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenTask = { navController.navigate(Routes.task(it)) },
            )
        }
        composable(Destination.Inbox.route) {
            InboxScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenTask = { navController.navigate(Routes.task(it)) },
            )
        }
        composable(Destination.Lists.route) {
            ListsScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenList = { navController.navigate(Routes.list(it)) },
            )
        }
        composable(
            Routes.LIST,
            arguments = listOf(navArgument("listId") { type = NavType.LongType }),
        ) { PlaceholderScreen(stringResource(R.string.list_detail), "screen-list") }
        composable(
            Routes.TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { PlaceholderScreen(stringResource(R.string.task_detail), "screen-task") }
        composable(Routes.SEARCH) {
            PlaceholderScreen(stringResource(R.string.search), "screen-search")
        }
    }
}
