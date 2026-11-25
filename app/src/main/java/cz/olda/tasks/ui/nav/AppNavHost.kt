package cz.olda.tasks.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.olda.tasks.ui.AppViewModelProvider
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import cz.olda.tasks.ui.Destination
import cz.olda.tasks.ui.inbox.InboxScreen
import cz.olda.tasks.ui.lists.ListDetailScreen
import cz.olda.tasks.ui.lists.ListsScreen
import cz.olda.tasks.ui.search.SearchScreen
import cz.olda.tasks.ui.settings.SettingsScreen
import cz.olda.tasks.ui.task.TaskDetailScreen
import cz.olda.tasks.ui.today.TodayScreen
import cz.olda.tasks.ui.upcoming.UpcomingScreen

object Routes {
    const val LIST = "list/{listId}"
    const val TASK = "task/{taskId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"

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
    val animate = LocalAnimationsEnabled.current
    // In bar order, which is also the order the eye expects them to move in.
    val tabs = remember { Destination.entries.map { it.route } }

    val betweenTabs: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Boolean = {
        initialState.destination.route in tabs && targetState.destination.route in tabs
    }

    /** +1 moving along the bar to the right, -1 to the left. */
    val tabDirection: AnimatedContentTransitionScope<NavBackStackEntry>.() -> Int = {
        val from = tabs.indexOf(initialState.destination.route)
        val to = tabs.indexOf(targetState.destination.route)
        if (to >= from) 1 else -1
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Today.route,
        modifier = modifier,
        enterTransition = {
            when {
                !animate -> EnterTransition.None
                betweenTabs() -> slideInHorizontally(AppMotion.spatial()) { tabDirection() * it / TabTravel } +
                    fadeIn(AppMotion.effects())
                else -> slideInHorizontally(AppMotion.spatial()) { it } + fadeIn(AppMotion.effects())
            }
        },
        exitTransition = {
            when {
                !animate -> ExitTransition.None
                betweenTabs() -> slideOutHorizontally(AppMotion.spatial()) { -tabDirection() * it / TabTravel } +
                    fadeOut(AppMotion.effects())
                // The screen being left steps back a quarter rather than leaving entirely,
                // so it reads as staying behind the one that arrives.
                else -> slideOutHorizontally(AppMotion.spatial()) { -it / 4 } + fadeOut(AppMotion.effects())
            }
        },
        popEnterTransition = {
            when {
                !animate -> EnterTransition.None
                betweenTabs() -> slideInHorizontally(AppMotion.spatial()) { tabDirection() * it / TabTravel } +
                    fadeIn(AppMotion.effects())
                else -> slideInHorizontally(AppMotion.spatial()) { -it / 4 } + fadeIn(AppMotion.effects())
            }
        },
        popExitTransition = {
            when {
                !animate -> ExitTransition.None
                betweenTabs() -> slideOutHorizontally(AppMotion.spatial()) { -tabDirection() * it / TabTravel } +
                    fadeOut(AppMotion.effects())
                else -> slideOutHorizontally(AppMotion.spatial()) { it } + fadeOut(AppMotion.effects())
            }
        },
    ) {
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
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }
        composable(
            Routes.LIST,
            arguments = listOf(navArgument("listId") { type = NavType.LongType }),
        ) {
            ListDetailScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenTask = { navController.navigate(Routes.task(it)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) {
            TaskDetailScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onBack = { navController.popBackStack() },
                onOpenTask = { navController.navigate(Routes.task(it)) },
            )
        }
        // Phase 1: deliberately unreachable — the picker stores nothing yet
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(Routes.SEARCH) {
            SearchScreen(
                viewModel = viewModel(factory = AppViewModelProvider.Factory),
                onOpenTask = { navController.navigate(Routes.task(it)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * How far a tab's content travels, as a fraction of the screen's width.
 *
 * Opening a task throws the new screen the whole way in and steps the old one back a
 * quarter: that is a push, and it means "deeper". Moving along the bar is sideways, not
 * downwards, so both screens slide the same short distance instead. Keep the two different
 * or the app stops telling depth from direction, which is most of what its motion is for.
 */
private const val TabTravel = 5
