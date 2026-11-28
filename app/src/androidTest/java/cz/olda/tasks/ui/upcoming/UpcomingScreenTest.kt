package cz.olda.tasks.ui.upcoming

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.SettingsRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.LocalSnackbarHostState
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UpcomingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val today = LocalDate.of(2026, 11, 23)
    private val clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        inbox = ListRepository(db.listDao(), db.groupDao(), db.headingDao()).inboxId()
    }

    @After fun close() { db.close() }

    private suspend fun add(title: String, due: LocalDate?) = repo.add(title, inbox, due, null, 4)

    /** Shows the screen and waits for its first load, so every later assertion sees real data. */
    private fun show() {
        setScreen()
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodes(hasTestTag("upcoming-empty")).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(hasContentDescription("Complete task")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitGone(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
    }

    private fun awaitShown(tag: String) = rule.waitUntil(timeoutMillis = 10_000) {
        rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun setScreen() {
        val viewModel = UpcomingViewModel(repo, SettingsRepository(db.settingDao()))
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            UpcomingScreen(viewModel = viewModel, onOpenTask = {})
                        }
                    }
                }
            }
        }
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test fun groupsTasksUnderTheirDay() {
        val ids = runBlocking {
            listOf(
                add("second day", today.plusDays(2)),
                add("first day a", today.plusDays(1)),
                add("first day b", today.plusDays(1)),
            )
        }
        show()
        val (second, firstA, firstB) = ids
        val firstHeader = "upcoming-day-${today.plusDays(1)}"
        val secondHeader = "upcoming-day-${today.plusDays(2)}"
        rule.onNodeWithTag(firstHeader).assertIsDisplayed()
        rule.onNodeWithTag(firstHeader).assertTextEquals(
            today.plusDays(1).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())),
        )
        rule.onNodeWithTag(secondHeader).assertIsDisplayed()
        assertTrue(top(firstHeader) < top("task-$firstA"))
        assertTrue(top("task-$firstB") < top(secondHeader))
        assertTrue(top(secondHeader) < top("task-$second"))
    }

    @Test fun todaysTasksDoNotAppear() {
        runBlocking {
            add("due today", today)
            add("overdue", today.minusDays(1))
            add("due tomorrow", today.plusDays(1))
        }
        show()
        rule.onNodeWithText("due tomorrow").assertIsDisplayed()
        rule.onNodeWithText("due today").assertDoesNotExist()
        rule.onNodeWithText("overdue").assertDoesNotExist()
    }

    @Test fun upcomingHasAHeader() {
        show()
        rule.onNodeWithTag("upcoming-header").assertTextEquals("Upcoming")
    }

    @Test fun emptyUpcomingSaysSo() {
        show()
        rule.onNodeWithText("Nothing coming up").assertIsDisplayed()
    }

    @Test fun aDayWithTasksHasNoEmptyState() {
        runBlocking { add("later", today.plusDays(3)) }
        show()
        rule.onNodeWithText("later").assertIsDisplayed()
        rule.onNodeWithText("Nothing coming up").assertDoesNotExist()
    }

    @Test fun noEmptyTextBeforeTheFirstEmission() {
        rule.setContent { AppTheme { UpcomingContent(days = null, onToggleDone = {}, onOpenTask = {}) } }
        rule.onNodeWithText("Nothing coming up").assertDoesNotExist()
    }

    @Test fun emptyTextShowsOnceLoadedEmpty() {
        rule.setContent { AppTheme { UpcomingContent(days = emptyList(), onToggleDone = {}, onOpenTask = {}) } }
        rule.onNodeWithText("Nothing coming up").assertIsDisplayed()
    }

    @Test fun completingARowRemovesItWithAnUndo() {
        val id = runBlocking { add("finish me", today.plusDays(1)) }
        show()
        rule.onNode(hasContentDescription("Complete task") and hasAnyAncestor(hasTestTag("task-$id")))
            .performClick()
        awaitGone("finish me")
        rule.onNodeWithText("finish me").assertDoesNotExist()
        rule.onNodeWithText("Undo").assertIsDisplayed().performClick()
        awaitShown("task-$id")
        rule.onNodeWithTag("task-$id").assertIsDisplayed()
    }

    // ---- Phase 3: hiding repeats ----

    private suspend fun addRepeating(title: String, due: LocalDate) =
        repo.add(title, inbox, due, null, 4, recurrence = "FREQ=DAILY")

    @Test fun eachDayGetsItsOwnFoldedRow() {
        val tomorrow = today.plusDays(1)
        val later = today.plusDays(3)
        runBlocking {
            addRepeating("Vynest kose", tomorrow)
            add("Zavolat", tomorrow)
            addRepeating("Protahnout se", later)
        }
        show()
        awaitShown("repeats-chip")

        rule.onNodeWithTag("repeats-chip").performClick()

        awaitShown("folded-repeats-$tomorrow")
        rule.onNodeWithTag("folded-repeats-$tomorrow").assertIsDisplayed()
        rule.onNodeWithTag("folded-repeats-$later").assertIsDisplayed()
        rule.onNodeWithText("Zavolat").assertIsDisplayed()
        rule.onAllNodesWithText("Vynest kose").assertCountEquals(0)
    }

    @Test fun aDayWithNoRepeatsHasNoFoldedRow() {
        val tomorrow = today.plusDays(1)
        runBlocking { add("Zavolat", tomorrow) }
        show()
        awaitShown("repeats-chip")

        rule.onNodeWithTag("repeats-chip").performClick()

        rule.onAllNodes(hasTestTag("folded-repeats-$tomorrow")).assertCountEquals(0)
        rule.onNodeWithText("Zavolat").assertIsDisplayed()
    }

    /** A day whose only task repeats must still appear, with its row saying so. */
    @Test fun aDayOfNothingButRepeatsStillAppears() {
        val tomorrow = today.plusDays(1)
        runBlocking { addRepeating("Vynest kose", tomorrow) }
        show()
        awaitShown("repeats-chip")

        rule.onNodeWithTag("repeats-chip").performClick()

        awaitShown("folded-repeats-$tomorrow")
        rule.onNodeWithTag("folded-repeats-$tomorrow").assertTextContains("1", substring = true)
        rule.onAllNodes(hasTestTag("upcoming-empty")).assertCountEquals(0)
        // Without its date the row is "1 repeat hidden" floating with nothing to attach it to, and
        // two such days are indistinguishable.
        rule.onNodeWithTag("upcoming-day-$tomorrow").assertIsDisplayed()
    }

    /** Two days of nothing but repeats must still be telling apart. */
    @Test
    fun twoDaysOfNothingButRepeatsBothKeepTheirDates() {
        val tomorrow = today.plusDays(1)
        val later = today.plusDays(3)
        runBlocking {
            addRepeating("Vynest kose", tomorrow)
            addRepeating("Protahnout se", later)
        }
        show()
        awaitShown("repeats-chip")

        rule.onNodeWithTag("repeats-chip").performClick()

        awaitShown("folded-repeats-$tomorrow")
        rule.onNodeWithTag("upcoming-day-$tomorrow").assertIsDisplayed()
        rule.onNodeWithTag("upcoming-day-$later").assertIsDisplayed()
    }

    @Test fun tappingADaysFoldedRowShowsThatDaysRepeats() {
        val tomorrow = today.plusDays(1)
        runBlocking { addRepeating("Vynest kose", tomorrow) }
        show()
        awaitShown("repeats-chip")
        rule.onNodeWithTag("repeats-chip").performClick()
        awaitShown("folded-repeats-$tomorrow")

        rule.onNodeWithTag("folded-repeats-$tomorrow").performClick()

        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Vynest kose").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Vynest kose").assertIsDisplayed()
    }
}
