package cz.olda.tasks.ui.lists

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.ui.LocalSnackbarHostState
import cz.olda.tasks.ui.inbox.InboxScreen
import cz.olda.tasks.ui.inbox.InboxViewModel
import cz.olda.tasks.ui.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ListsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepository
    private lateinit var lists: ListRepository
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")
    private val clock = Clock.fixed(LocalDate.of(2026, 11, 23).atTime(10, 0).atZone(zone).toInstant(), zone)

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        repo = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        inbox = lists.inboxId()
    }

    @After fun close() { db.close() }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().size

    // The row is clickable, so its parts merge into one node; the count is found unmerged.
    private fun countU(matcher: SemanticsMatcher) =
        rule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun groupId() = runBlocking { db.groupDao().observeAll().first().single().id }

    /** Shows the screen and waits for its first load: a row, a group header or the empty state. */
    private fun show(withInbox: Boolean = false) {
        val viewModel = ListsViewModel(lists, repo)
        rule.setContent {
            AppTheme {
                val hostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalSnackbarHostState provides hostState) {
                    Scaffold(snackbarHost = { SnackbarHost(hostState) }) { padding ->
                        Column(Modifier.padding(padding)) {
                            Box(Modifier.weight(1f)) { ListsScreen(viewModel = viewModel, onOpenList = {}) }
                            if (withInbox) {
                                Box(Modifier.weight(1f)) {
                                    InboxScreen(InboxViewModel(repo, lists), onOpenTask = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            count(hasTestTag("lists-empty")) + count(tagStartsWith("list-row-")) + count(tagStartsWith("group-")) > 0
        }
    }

    private fun awaitGone(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        count(hasText(text)) == 0
    }

    private fun awaitShown(text: String) = rule.waitUntil(timeoutMillis = 10_000) {
        count(hasText(text)) > 0
    }

    @Test fun showsGroupsWithTheirLists() {
        runBlocking {
            val home = lists.createGroup("Home")
            lists.createList("Garden", null, home)
        }
        show()
        rule.onNodeWithText("Home").assertIsDisplayed()
        rule.onNodeWithText("Garden").assertIsDisplayed()
    }

    @Test fun ungroupedListsAppearWithoutAGroupHeader() {
        runBlocking { lists.createList("Solo") }
        show()
        rule.onNodeWithText("Solo").assertIsDisplayed()
        assertEquals(0, count(tagStartsWith("group-")))
    }

    @Test fun inboxIsNotListedHere() {
        runBlocking { lists.createList("Work") }
        show()
        rule.onNodeWithText("Work").assertIsDisplayed()
        rule.onNodeWithText("Inbox").assertDoesNotExist()
        assertEquals(1, count(tagStartsWith("list-row-")))
    }

    @Test fun aListRowCountsItsOpenTasks() {
        val id = runBlocking { lists.createList("Work") }
        runBlocking {
            repo.add("one", id, null, null, 4)
            repo.add("two", id, null, null, 4)
            val done = repo.add("finished", id, null, null, 4)
            repo.setDone(done, true)
        }
        show()
        rule.waitUntil(timeoutMillis = 10_000) { countU(hasTestTag("list-count-$id") and hasText("2")) == 1 }
        rule.onNodeWithTag("list-count-$id", useUnmergedTree = true).assertTextEquals("2")
    }

    @Test fun aListWithNoOpenTasksCountsZero() {
        val id = runBlocking { lists.createList("Empty one") }
        show()
        rule.waitUntil(timeoutMillis = 10_000) { countU(hasTestTag("list-count-$id") and hasText("0")) == 1 }
    }

    @Test fun stateIsNullBeforeTheFirstEmission() {
        assertNull(ListsViewModel(lists, repo).state.value)
    }

    @Test fun deletingAListMovesItsTasksToInbox() {
        val id = runBlocking { lists.createList("Doomed") }
        runBlocking {
            repo.add("first", id, null, null, 4)
            repo.add("second", id, null, null, 4)
        }
        show(withInbox = true)
        rule.onNodeWithTag("list-edit-$id").performClick()
        rule.onNodeWithTag("list-delete").performClick()
        // The confirmation says where the tasks go, before anything is deleted.
        rule.onNodeWithText(
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.delete_list_moves_tasks),
        ).assertIsDisplayed()
        assertEquals(1, runBlocking { lists.observeLists().first().count { it.id == id } })
        rule.onNodeWithTag("delete-confirm").performClick()
        awaitGone("Doomed")
        awaitShown("first")
        rule.onNodeWithText("first").assertIsDisplayed()
        rule.onNodeWithText("second").assertIsDisplayed()
        assertEquals(
            setOf("first", "second"),
            runBlocking { repo.observeInList(inbox).first().map { it.task.title }.toSet() },
        )
    }

    @Test fun cancellingTheDeleteKeepsTheList() {
        val id = runBlocking { lists.createList("Keeper") }
        show()
        rule.onNodeWithTag("list-edit-$id").performClick()
        rule.onNodeWithTag("list-delete").performClick()
        rule.onNodeWithTag("delete-cancel").performClick()
        rule.waitUntil(timeoutMillis = 10_000) { count(hasTestTag("delete-confirm")) == 0 }
        assertEquals(1, runBlocking { lists.observeLists().first().count { it.id == id } })
    }

    @Test fun aNewListIsCreatedFromTheSheet() {
        show()
        rule.onNodeWithTag("new-list").performClick()
        rule.onNodeWithTag("list-name-field").performTextInput("Fresh")
        rule.onNodeWithTag("list-save").performClick()
        awaitShown("Fresh")
        rule.onNodeWithText("Fresh").assertIsDisplayed()
    }

    @Test fun aNewListCanGoIntoAGroup() {
        runBlocking { lists.createGroup("Home") }
        show()
        val gid = groupId()
        rule.onNodeWithTag("new-list").performClick()
        rule.onNodeWithTag("list-name-field").performTextInput("Boiler")
        rule.onNodeWithTag("list-group-option-$gid").performClick()
        rule.onNodeWithTag("list-save").performClick()
        awaitShown("Boiler")
        assertEquals(gid, runBlocking { lists.observeLists().first().single { it.name == "Boiler" }.groupId })
    }

    @Test fun aListIsRenamedFromTheSheet() {
        val id = runBlocking { lists.createList("Old") }
        show()
        rule.onNodeWithTag("list-edit-$id").performClick()
        rule.onNodeWithTag("list-name-field").performTextClearance()
        rule.onNodeWithTag("list-name-field").performTextInput("New")
        rule.onNodeWithTag("list-save").performClick()
        awaitShown("New")
        awaitGone("Old")
    }

    @Test fun aGroupIsCreatedAndItsListsSurviveItsDeletion() {
        show()
        rule.onNodeWithTag("new-group").performClick()
        rule.onNodeWithTag("group-name-field").performTextInput("Errands")
        rule.onNodeWithTag("group-save").performClick()
        awaitShown("Errands")
        val gid = groupId()
        runBlocking { lists.createList("Shop", null, gid) }
        awaitShown("Shop")
        rule.onNodeWithTag("group-$gid").performClick()
        rule.onNodeWithTag("group-delete").performClick()
        rule.onNodeWithTag("delete-confirm").performClick()
        awaitGone("Errands")
        rule.onNodeWithText("Shop").assertIsDisplayed()
    }
}
