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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.runtime.mutableStateMapOf
import cz.olda.tasks.ui.components.TaskFlight
import kotlinx.coroutines.delay
import java.time.LocalDate
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
    flights: FlightController = remember { FlightController() },
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

    // Where the bar's parts are on screen, so the pill knows where to start and where to land.
    // Measured in root coordinates because it crosses from the content area into the bar, which
    // are different parts of the Scaffold.
    val tabBounds = remember { mutableStateMapOf<Destination, Rect>() }
    var addButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var pulsing by remember { mutableStateOf<Destination?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(slimOnScroll),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingBottomBar(
                selected = selected,
                slimmed = slimmed.value,
                pulsing = pulsing,
                onSelect = { destination ->
                    // Pop to the graph start so tabs do not pile up on the back stack.
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = {
                    // Capture beats decoration: whatever is in flight goes.
                    flights.cancel()
                    quickAddOpen = true
                },
                onTabBounds = { destination, bounds -> tabBounds[destination] = bounds },
                onAddButtonBounds = { addButtonBounds = it },
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
        QuickAddSheet(
            onDismiss = { quickAddOpen = false },
            onSaved = { saved ->
                flights.start(saved.title, arrivalTab(saved, LocalDate.now()))
            },
        )
    }

        // Above the Scaffold, so the pill can cross from the content area into the bar.
        val inFlight = flights.current
        val from = addButtonBounds
        val to = inFlight?.let { tabBounds[it.destination] }
        if (inFlight != null && from != null && to != null) {
            TaskFlight(
                label = inFlight.label,
                from = from,
                to = to,
                onFinished = {
                    pulsing = inFlight.destination
                    flights.cancel()
                },
            )
        }
    }

    // The pulse is brief and then the tab is an ordinary tab again.
    LaunchedEffect(pulsing) {
        if (pulsing != null) {
            delay(PulseMillis)
            pulsing = null
        }
    }
}

/** A task on its way to the tab that holds it. */
data class Flight(val label: String, val destination: Destination)

/**
 * Holds the one task currently in flight, and exists so it can be driven from a test.
 *
 * Catching a real flight through the real sheet is not possible here: the save is an
 * ordinary asynchronous write running in real time, while the pill is an animation running
 * on the test clock, and holding one still stalls the other - the pill has always landed
 * before an assertion can see it. With this, the shell's own rules (one at a time, cancelled
 * by the add button, landing on the right tab) are tested directly and deterministically,
 * and only the single line that calls [start] from the sheet is left to the eye.
 */
@Stable
class FlightController {
    var current: Flight? by mutableStateOf(null)
        private set

    /** Replaces whatever was in flight: there is never more than one. */
    fun start(label: String, destination: Destination) {
        current = Flight(label, destination)
    }

    fun cancel() {
        current = null
    }
}

/** How long the arrival tab stays pulsed after the pill lands. */
private const val PulseMillis = 420L

/** Pixels of drag before the bar reacts, so a finger resting on the screen cannot flutter it. */
private const val SlimThreshold = 2f

/** The dark rounded pill from docs/design.md, section Look. */
@Composable
private fun FloatingBottomBar(
    selected: Destination,
    slimmed: Boolean,
    pulsing: Destination?,
    onSelect: (Destination) -> Unit,
    onAdd: () -> Unit,
    onTabBounds: (Destination, Rect) -> Unit,
    onAddButtonBounds: (Rect) -> Unit,
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
                    pulsing = pulsing,
                    onSelect = onSelect,
                    onTabBounds = onTabBounds,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                AddButton(onClick = onAdd, onBounds = onAddButtonBounds)
            }
        }
    }
}

/** Where one tab sits inside the strip, in the strip's own coordinates. */
private data class TabSlot(val x: Dp, val width: Dp, val height: Dp)

/**
 * The travelling marker behind the selected tab. Its animations seed from the first slot it
 * is given, so it appears where it belongs and only moves when the selection does.
 */
@Composable
private fun Pill(slot: TabSlot, color: Color) {
    val animate = LocalAnimationsEnabled.current
    val x by animateDpAsState(slot.x, AppMotion.spatial(), label = "pill-x")
    val width by animateDpAsState(slot.width, AppMotion.spatial(), label = "pill-width")
    Box(
        modifier = Modifier
            .offset(x = if (animate) x else slot.x)
            // Coerced because a spring can undershoot below zero on its way to a target.
            .size(
                width = (if (animate) width else slot.width).coerceAtLeast(0.dp),
                height = slot.height.coerceAtLeast(0.dp),
            )
            .clip(CircleShape)
            .background(color)
            .testTag("bottom-bar-pill"),
    )
}

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
    pulsing: Destination?,
    onSelect: (Destination) -> Unit,
    onTabBounds: (Destination, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    val density = LocalDensity.current
    var slots by remember { mutableStateOf(emptyMap<Destination, TabSlot>()) }
    val slot = slots[selected]

    Box(modifier = modifier) {
        // Drawn only once the tabs have been measured. Composing it earlier would seed its
        // animations at zero and swell the pill in from nothing on every launch, before it
        // had anywhere to be.
        if (slot != null) Pill(slot, colors.selectedPill)
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
                            // Strip-local for the pill behind the tabs...
                            val next = TabSlot(
                                x = with(density) { coordinates.positionInParent().x.toDp() },
                                width = with(density) { coordinates.size.width.toDp() },
                                height = with(density) { coordinates.size.height.toDp() },
                            )
                            if (slots[destination] != next) slots = slots + (destination to next)
                            // ...and root coordinates for the flight, which comes from
                            // outside the bar entirely.
                            onTabBounds(destination, coordinates.boundsInRoot())
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
private fun AddButton(
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBottomBarColors.current
    Box(
        modifier = modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
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
