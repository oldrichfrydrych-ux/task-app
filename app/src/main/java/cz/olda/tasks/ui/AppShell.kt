package cz.olda.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import cz.olda.tasks.R
import cz.olda.tasks.ui.nav.AppNavHost
import cz.olda.tasks.ui.quickadd.QuickAddSheet
import cz.olda.tasks.ui.theme.LocalBottomBarColors

/**
 * Lets any screen post a message without owning a snackbar host. The shell provides it
 * around the navigation graph.
 */
val LocalSnackbarHostState: ProvidableCompositionLocal<SnackbarHostState> =
    compositionLocalOf { error("No SnackbarHostState provided") }

/** Touch targets stay at or above this, as docs/design.md asks. */
private val TouchTarget = 48.dp

/**
 * The frame every screen sits in: the floating bottom bar with the four destinations
 * and the coral add button that opens quick-add, around the navigation graph.
 */
@Composable
fun AppShell(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Saved by name, so the tab survives process death and not just rotation.
    var selectedName by rememberSaveable { mutableStateOf(Destination.Today.name) }
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // The graph decides which tab is current, so Back and taps cannot disagree. Detail screens
    // belong to no tab and keep the last top-level one, which is the only thing written to the
    // saved name: it follows the route, never a tap, so there is a single writer.
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val routeTab = Destination.entries.firstOrNull { it.route == route }
    LaunchedEffect(routeTab) { if (routeTab != null) selectedName = routeTab.name }
    val selected = routeTab ?: Destination.ofName(selectedName)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingBottomBar(
                selected = selected,
                onSelect = { destination ->
                    // Pop to the graph start so tabs do not pile up on the back stack.
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { quickAddOpen = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
                AppNavHost(navController = navController)
            }
        }
    }

    if (quickAddOpen) {
        QuickAddSheet(onDismiss = { quickAddOpen = false })
    }
}

/** The dark rounded pill from docs/design.md, section Look. */
@Composable
private fun FloatingBottomBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The app draws edge to edge, so the bar floats above the system's own
            // bar instead of under it, whichever navigation the phone uses.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("bottom-bar"),
        shape = CircleShape,
        color = colors.container,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                TabItem(
                    destination = destination,
                    selected = destination == selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            AddButton(onClick = onAdd)
        }
    }
}

@Composable
private fun TabItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    val content = if (selected) colors.onSelected else colors.onUnselected
    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.selectedPill else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = TouchTarget)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBottomBarColors.current
    Box(
        modifier = modifier
            .size(TouchTarget)
            .clip(CircleShape)
            .background(colors.addButton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.Add,
            contentDescription = stringResource(R.string.add_task),
            tint = colors.onAddButton,
            modifier = Modifier.size(24.dp),
        )
    }
}
