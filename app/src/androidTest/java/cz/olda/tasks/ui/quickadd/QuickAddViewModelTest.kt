package cz.olda.tasks.ui.quickadd

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cz.olda.tasks.R
import cz.olda.tasks.data.AppDatabase
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TagRepository
import cz.olda.tasks.data.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** The sheet's brain: the parser runs here, and what the chips show is what gets saved. */
@RunWith(AndroidJUnit4::class)
class QuickAddViewModelTest {
    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var lists: ListRepository
    private lateinit var tags: TagRepository
    private lateinit var viewModel: QuickAddViewModel
    private var prace = 0L
    private var inbox = 0L

    private val zone = ZoneId.of("Europe/Prague")

    /** A Friday, 16:30 — late enough that three in the afternoon has gone. */
    private val clock = Clock.fixed(
        LocalDate.of(2026, 11, 27).atTime(16, 30).atZone(zone).toInstant(),
        zone,
    )

    @Before fun open() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.seedInbox(context.getString(R.string.inbox)))
            .build()
        tasks = TaskRepository(db.taskDao(), clock)
        lists = ListRepository(db.listDao(), db.groupDao(), db.headingDao())
        tags = TagRepository(db.tagDao())
        prace = lists.createList("🌊Práce", "#3D6BE0")
        inbox = lists.inboxId()
        viewModel = QuickAddViewModel(tasks, lists, tags, clock)
    }

    @After fun close() = db.close()

    /**
     * The state flow caches its last value, so asking for it straight after a change can hand
     * back the state from before that change. Every read says which input it is waiting for.
     */
    private suspend fun state(expecting: String) =
        viewModel.state.first { it.lists.isNotEmpty() && it.input == expecting }

    @Test fun typingFillsTheFieldsAndCutsTheTitle() = runBlocking {
        viewModel.setInput("zitra ve 3 zavolat na urad #prace")
        val state = state("zitra ve 3 zavolat na urad #prace")
        assertEquals("zitra ve 3 zavolat na urad #prace", state.input)
        assertEquals("zavolat na urad", state.title)
        assertEquals(LocalDate.of(2026, 11, 28), state.dueDate)
        assertEquals(900, state.dueMinute)
        assertEquals(prace, state.listId)
    }

    @Test fun savingWritesEverythingTheParserFound() = runBlocking {
        viewModel.setInput("kazde pondeli v 9 porada #prace p2")
        state("kazde pondeli v 9 porada #prace p2")
        viewModel.save()
        val task = tasks.observeInList(prace).first().single().task
        assertEquals("porada", task.title)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), task.dueDate)
        assertEquals(540, task.dueMinute)
        assertEquals(2, task.priority)
        assertEquals("FREQ=WEEKLY;BYDAY=MO", task.recurrence)
    }

    @Test fun savingAttachesTheParsedTags() = runBlocking {
        viewModel.setInput("zavolat @telefon @rychle")
        state("zavolat @telefon @rychle")
        viewModel.save()
        val task = tasks.observeInList(inbox).first().single().task
        assertEquals("zavolat", task.title)
        assertEquals(listOf("rychle", "telefon"), tags.observeTagsOf(task.id).first().map { it.name })
    }

    /** A pin changes that field's value and never the title cut. */
    @Test fun aPinnedDateSurvivesFurtherTyping() = runBlocking {
        viewModel.setInput("zitra zavolat")
        state("zitra zavolat")
        viewModel.setDate(LocalDate.of(2026, 12, 4))
        viewModel.setInput("zitra zavolat Petrovi")
        val state = state("zitra zavolat Petrovi")
        assertEquals(LocalDate.of(2026, 12, 4), state.dueDate)
        assertEquals("zavolat Petrovi", state.title)
    }

    @Test fun clearingTheFieldUnpinsEverything() = runBlocking {
        viewModel.setInput("zitra zavolat")
        state("zitra zavolat")
        viewModel.setDate(LocalDate.of(2026, 12, 4))
        viewModel.setInput("")
        state("")
        viewModel.setInput("zitra zavolat")
        assertEquals(LocalDate.of(2026, 11, 28), state("zitra zavolat").dueDate)
    }

    @Test fun aClearedRepeatStaysCleared() = runBlocking {
        viewModel.setInput("kazdy den cvicit")
        assertEquals("FREQ=DAILY", state("kazdy den cvicit").recurrence)
        viewModel.setRecurrence(null)
        viewModel.setInput("kazdy den cvicit doma")
        assertNull(state("kazdy den cvicit doma").recurrence)
    }

    /** A repeat picked on the chip is his, exactly as a date picked on the date chip is. */
    @Test fun aPickedRepeatBeatsWhatIsTyped() = runBlocking {
        viewModel.setInput("kazdy den cvicit")
        viewModel.setRecurrence("FREQ=WEEKLY;BYDAY=MO")
        assertEquals("FREQ=WEEKLY;BYDAY=MO", state("kazdy den cvicit").recurrence)
        viewModel.setInput("kazdy den cvicit doma")
        assertEquals("FREQ=WEEKLY;BYDAY=MO", state("kazdy den cvicit doma").recurrence)
    }

    /**
     * The wart Phase 2 wrote down: a cleared repeat could not be brought back, because the chip
     * that would have done it was gone. Emptying the field is what unpins it.
     */
    @Test fun clearingTheFieldLetsATypedRepeatCountAgain() = runBlocking {
        viewModel.setInput("kazdy den cvicit")
        viewModel.setRecurrence(null)
        assertNull(state("kazdy den cvicit").recurrence)

        viewModel.setInput("")
        viewModel.setInput("kazdy den cvicit doma")

        // Waits for the repeat to be back rather than for the input alone: the state flow combines
        // the line and the pins from separate coroutines, so an emission can carry the new line
        // beside the pin from before it was cleared. The screen settles a frame later; a test that
        // reads the first matching emission can catch the one in between. A repeat that never came
        // back times this out, which is the failure this test is for.
        val after = viewModel.state.first {
            it.lists.isNotEmpty() && it.input == "kazdy den cvicit doma" && it.recurrence != null
        }
        assertEquals("FREQ=DAILY", after.recurrence)
        assertEquals("the rule is the parser's again, so his own words are back", "kazdy den", after.recurrenceText)
    }

    @Test fun aPickedRepeatIsWhatGetsSaved() = runBlocking {
        viewModel.setInput("cvicit")
        viewModel.setRecurrence("FREQ=MONTHLY;INTERVAL=3")
        state("cvicit")

        viewModel.save()

        val saved = tasks.observeInList(inbox).first().single().task
        assertEquals("FREQ=MONTHLY;INTERVAL=3", saved.recurrence)
    }

    /** With a rule from the picker there are no typed words to echo, so the chip says it in English. */
    @Test fun aPickedRepeatHasNoTypedWordsToEcho() = runBlocking {
        viewModel.setInput("kazdy den cvicit")
        assertEquals("kazdy den", state("kazdy den cvicit").recurrenceText)

        viewModel.setRecurrence("FREQ=DAILY")
        viewModel.setInput("kazdy den cvicit doma")

        val after = state("kazdy den cvicit doma")
        assertNull(after.recurrenceText)
        assertEquals("FREQ=DAILY", after.recurrence)
    }

    /** Review Focus 2: the chips still say what it understood, but there is no title to save. */
    @Test fun aLineOfNothingButTokensCannotBeSaved() = runBlocking {
        viewModel.setInput("zitra #prace p1")
        val state = state("zitra #prace p1")
        assertEquals("", state.title)
        assertFalse(state.canSave)
        assertEquals(LocalDate.of(2026, 11, 28), state.dueDate)
        assertEquals(prace, state.listId)
        assertNull(viewModel.save())
    }

    /**
     * Review Focus 5: the list is gone by the time Save is pressed. The task must land in Inbox
     * rather than carry a dangling listId. Save reparses against the lists that exist then, so
     * `#prace` no longer names anything and stays in the title — the same as any unknown list,
     * and better than the word vanishing into a list that is not there any more.
     */
    @Test fun aParsedListThatIsDeletedBeforeSavingFallsBackToInbox() = runBlocking {
        viewModel.setInput("zavolat #prace")
        assertEquals(prace, state("zavolat #prace").listId)
        lists.deleteList(prace)
        viewModel.save()
        val saved = tasks.observeInList(inbox).first().single().task
        assertEquals("zavolat #prace", saved.title)
        assertEquals(inbox, saved.listId)
    }

    @Test fun anUnknownListLeavesTheWordAndGoesToInbox() = runBlocking {
        viewModel.setInput("zavolat #xyz")
        val state = state("zavolat #xyz")
        assertEquals("zavolat #xyz", state.title)
        assertEquals(inbox, state.listId)
    }

    @Test fun theRepeatTextIsWhatOldaTyped() = runBlocking {
        viewModel.setInput("kazdy den cvicit")
        assertEquals("kazdy den", state("kazdy den cvicit").recurrenceText)
    }
}
