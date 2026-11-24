package cz.olda.tasks.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.olda.tasks.ui.theme.AppMotion
import cz.olda.tasks.ui.theme.LocalAnimationsEnabled
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import cz.olda.tasks.R
import cz.olda.tasks.ui.nav.AppNavHost
import cz.olda.tasks.ui.components.CompletionTracker
import cz.olda.tasks.ui.components.LocalCompletionTracker
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
    // Lives beside the snackbar host for the same reason: every screen that lists tasks needs
    // it, and none of them should have to own it.
    val completionTracker = remember { CompletionTracker() }

    // The graph decides which tab is current, so Back and taps cannot disagree. Detail screens
    // belong to no tab and keep the last top-level one, which is the only thing written to the
    // saved name: it follows the route, never a tap, so there is a single writer.
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val routeTab = Destination.entries.firstOrNull { it.route == route }
    LaunchedEffect(routeTab) { if (routeTab != null) selectedName = routeTab.name }
    val selected = routeTab ?: Destination.ofName(selectedName)

    // Scrolling down slims the bar, any upward scroll brings it back. Read from onPreScroll
    // so it reacts to the gesture rather than to what the content managed to consume: a short
    // list that cannot scroll should not leave the bar stuck.
    val slimmed = remember { mutableStateOf(false) }
    val slimOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -SlimThreshold) slimmed.value = true
                else if (available.y > SlimThreshold) slimmed.value = false
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(slimOnScroll),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingBottomBar(
                selected = selected,
                slimmed = slimmed.value,
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
            CompositionLocalProvider(
                LocalSnackbarHostState provides snackbarHostState,
                LocalCompletionTracker provides completionTracker,
            ) {
                AppNavHost(navController = navController)
            }
        }
    }

    if (quickAddOpen) {
        QuickAddSheet(onDismiss = { quickAddOpen = false })
    }
}

/** Pixels of drag before the bar reacts, so a finger resting on the screen cannot flutter it. */
private const val SlimThreshold = 2f

/** The dark rounded pill from docs/design.md, section Look. */
@Composable
private fun FloatingBottomBar(
    selected: Destination,
    slimmed: Boolean,
    onSelect: (Destination) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    val density = LocalDensity.current

    // The slot keeps the bar's full height for good, so Scaffold's innerPadding never changes
    // and the scrolling content never relayouts while the bar shrinks inside it. The tall
    // height is whatever the bar measures when it is not slimmed - taken as a maximum, because
    // growing back through the un-slim animation would otherwise record intermediate heights.
    var tallHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The app draws edge to edge, so the bar floats above the system's own
            // bar instead of under it, whichever navigation the phone uses.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .then(if (tallHeight > 0.dp) Modifier.height(tallHeight) else Modifier)
            .testTag("bottom-bar-slot"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    if (!slimmed) {
                        val measured = with(density) { it.height.toDp() }
                        if (measured > tallHeight) tallHeight = measured
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("bottom-bar"),
            shape = CircleShape,
            color = colors.container,
            shadowElevation = 8.dp,
        ) {
            // What actually slims the bar. The tabs themselves keep the 48dp touch target
            // docs/design.md requires, so hiding their labels alone would change no height
            // at all; the row's own padding is the part that can go.
            val targetPadding = if (slimmed) 0.dp else 6.dp
            val animatedPadding by animateDpAsState(targetPadding, AppMotion.spatial(), label = "bar-padding")
            // Coerced because an expressive spring is underdamped: on the way to 0 it
            // undershoots past it, and a negative padding throws.
            val rowPadding = (if (LocalAnimationsEnabled.current) animatedPadding else targetPadding)
                .coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = rowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabStrip(
                    selected = selected,
                    slimmed = slimmed,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                AddButton(onClick = onAdd)
            }
        }
    }
}

/** Where one tab sits inside the strip, in the strip's own coordinates. */
private data class TabSlot(val x: Dp, val width: Dp, val height: Dp)

/**
 * The four destinations with a single pill behind them.
 *
 * The pill travels to the selected tab's *measured* bounds rather than to a computed quarter
 * of the strip: integer rounding on a quarter can leave it a pixel short of the tab it is
 * meant to mark, and measuring also survives any later change to how the tabs are laid out.
 */
@Composable
private fun TabStrip(
    selected: Destination,
    slimmed: Boolean,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    val density = LocalDensity.current
    var slots by remember { mutableStateOf(emptyMap<Destination, TabSlot>()) }
    val slot = slots[selected]

    val targetX = slot?.x ?: 0.dp
    val targetWidth = slot?.width ?: 0.dp
    val animatedX by animateDpAsState(targetX, AppMotion.spatial(), label = "pill-x")
    val animatedWidth by animateDpAsState(targetWidth, AppMotion.spatial(), label = "pill-width")
    val animate = LocalAnimationsEnabled.current

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset(x = if (animate) animatedX else targetX)
                .size(
                    // Same reason as the bar's padding: a spring can undershoot below zero.
                    width = (if (animate) animatedWidth else targetWidth).coerceAtLeast(0.dp),
                    height = (slot?.height ?: 0.dp).coerceAtLeast(0.dp),
                )
                .clip(CircleShape)
                .background(colors.selectedPill)
                .testTag("bottom-bar-pill"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Destination.entries.forEach { destination ->
                TabItem(
                    destination = destination,
                    selected = destination == selected,
                    slimmed = slimmed,
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            val next = TabSlot(
                                x = with(density) { coordinates.positionInParent().x.toDp() },
                                width = with(density) { coordinates.size.width.toDp() },
                                height = with(density) { coordinates.size.height.toDp() },
                            )
                            if (slots[destination] != next) slots = slots + (destination to next)
                        },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    destination: Destination,
    selected: Boolean,
    slimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    val content = if (selected) colors.onSelected else colors.onUnselected
    val animate = LocalAnimationsEnabled.current
    Column(
        // No background of its own: TabStrip draws one pill behind whichever tab is selected.
        modifier = modifier
            .clip(CircleShape)
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
        AnimatedVisibility(
            visible = !slimmed,
            enter = if (animate) {
                expandVertically(AppMotion.spatial()) + fadeIn(AppMotion.effects())
            } else {
                EnterTransition.None
            },
            exit = if (animate) {
                shrinkVertically(AppMotion.spatial()) + fadeOut(AppMotion.effects())
            } else {
                ExitTransition.None
            },
        ) {
            Text(
                text = stringResource(destination.label),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
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
