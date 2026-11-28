package cz.olda.tasks.ui.quickadd

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.olda.tasks.data.ListRepository
import cz.olda.tasks.data.TagRepository
import cz.olda.tasks.data.TaskRepository
import cz.olda.tasks.data.entity.ListEntity
import cz.olda.tasks.data.parser.TokenKind
import cz.olda.tasks.data.parser.parseQuickAdd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What a save actually wrote. Enough for the shell to say where the task went and to put its
 * title on the pill that flies there; not a destination, because deciding that is the shell's
 * job and belongs somewhere it can be tested without a device.
 */
data class SavedTask(val title: String, val dueDate: LocalDate?, val toInbox: Boolean)

/**
 * What the sheet shows. [input] is the line as typed and [title] is what a save would store —
 * the line with every recognised phrase cut out. [listId] is 0 only until the lists have loaded.
 */
data class QuickAddState(
    val input: String,
    val title: String,
    val dueDate: LocalDate?,
    val dueMinute: Int?,
    val listId: Long,
    val priority: Int,
    val recurrence: String?,
    /** The repeat in Olda's own words, for the chip; the rule cannot reconstruct his spelling. */
    val recurrenceText: String?,
    val tagNames: List<String>,
    val lists: List<ListEntity>,
    val canSave: Boolean,
)

/**
 * A repeat the picker set. One saved-state key, not two: "" means Olda has not touched the chip and
 * the parser still decides, NO_REPEAT means he picked "Does not repeat", anything else is the rule.
 *
 * One key rather than a "pinned" flag beside a rule, because the two would have to be written one
 * after the other, and [state]'s `combine` collects each key in its own coroutine - so it can emit
 * a state built from the new value of one and the old value of the other. The screen converges a
 * frame later and never shows it, but one key means that intermediate state cannot exist at all.
 */
private data class RepeatPin(val pinned: Boolean, val rule: String?) {
    companion object {
        fun of(stored: String): RepeatPin = when (stored) {
            UNPINNED -> RepeatPin(pinned = false, rule = null)
            NO_REPEAT -> RepeatPin(pinned = true, rule = null)
            else -> RepeatPin(pinned = true, rule = stored)
        }
    }
}

/** Olda has not touched the repeat chip; what he typed decides. */
private const val UNPINNED = ""

/** He picked "Does not repeat"; no rule, and the parser no longer gets a say. */
private const val NO_REPEAT = "none"

/** The fields Olda has taken over from the parser, carried through the flow together. */
private data class Pins(val list: Long, val priority: Int, val repeat: RepeatPin)

private const val NO_LIST = -1L
private const val NO_DATE = Long.MIN_VALUE
private const val NO_MINUTE = -1
private const val DEFAULT_PRIORITY = 4

/**
 * The draft of the task being typed. The draft lives in [savedState], so it survives the process
 * being killed, and it also survives the sheet being dismissed: a stray tap on the scrim must not
 * destroy what was typed. Only a successful [save] clears it.
 *
 * The parser runs on every keystroke and fills the chips. **A chip Olda touches is his**: that
 * field is pinned to what he chose and the parser stops setting it, so a correction cannot be
 * undone by typing one more word. Pinning changes the value and never the title cut — type
 * "zitra zavolat", pick Friday, and the task is "zavolat" on Friday, because "zitra" was typed as
 * a date, which is why it matched. Clearing the field unpins everything.
 */
class QuickAddViewModel(
    private val taskRepository: TaskRepository,
    private val listRepository: ListRepository,
    private val tagRepository: TagRepository,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    val state: StateFlow<QuickAddState> = combine(
        savedState.getStateFlow(INPUT, ""),
        savedState.getStateFlow(DATE, NO_DATE),
        savedState.getStateFlow(MINUTE, NO_MINUTE),
        combine(
            savedState.getStateFlow(LIST, NO_LIST),
            savedState.getStateFlow(PRIORITY, 0),
            savedState.getStateFlow(REPEAT, UNPINNED),
        ) { list, priority, repeat -> Pins(list, priority, RepeatPin.of(repeat)) },
        listRepository.observeLists(),
    ) { input, epochDay, minute, pins, lists ->
        build(input, epochDay, minute, pins, lists)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        empty(),
    )

    /** Stores the raw line and reparses it. An empty field forgets every chip Olda had touched. */
    fun setInput(input: String) {
        savedState[INPUT] = input
        if (input.isBlank()) unpinAll()
    }

    fun setDate(date: LocalDate?) { savedState[DATE] = date?.toEpochDay() ?: NO_DATE }

    fun setMinute(minute: Int?) { savedState[MINUTE] = minute ?: NO_MINUTE }

    fun setList(listId: Long) { savedState[LIST] = listId }

    fun setPriority(priority: Int) { savedState[PRIORITY] = priority }

    /**
     * Sets the repeat from the picker, or clears it with null, and pins it either way — a chip Olda
     * touched is his, exactly as with the date, the time, the list and the priority. Emptying the
     * field unpins it, which is what makes a cleared repeat reachable again: in Phase 2 the chip
     * vanished with the repeat and nothing on screen could bring it back.
     */
    fun setRecurrence(rule: String?) {
        savedState[REPEAT] = rule ?: NO_REPEAT
    }

    /** The line as stored, for the field to start from after a restart. */
    val draftInput: String get() = savedState[INPUT] ?: ""

    private fun unpinAll() {
        savedState[DATE] = NO_DATE
        savedState[MINUTE] = NO_MINUTE
        savedState[LIST] = NO_LIST
        savedState[PRIORITY] = 0
        savedState[REPEAT] = UNPINNED
    }

    /** Throws the draft away; [save] calls it after the task is written. */
    private fun clear() {
        savedState[INPUT] = ""
        unpinAll()
    }

    // One write at a time: a second tap, or a tap plus IME Done, waits here and then finds the draft gone.
    private val saveLock = Mutex()

    private fun empty() =
        QuickAddState("", "", null, null, 0L, DEFAULT_PRIORITY, null, null, emptyList(), emptyList(), false)

    private fun build(
        input: String,
        pinnedDay: Long,
        pinnedMinute: Int,
        pins: Pins,
        lists: List<ListEntity>,
    ): QuickAddState {
        val pinnedList = pins.list
        val pinnedPriority = pins.priority
        val parsed = parseQuickAdd(input, LocalDateTime.now(clock), lists.map { it.name })

        // A list that was deleted since it was picked or parsed falls back to Inbox, like its tasks do.
        val wanted = if (pinnedList != NO_LIST) pinnedList else lists.firstOrNull { it.name == parsed.listName }?.id
        val listId = lists.firstOrNull { it.id == wanted }?.id
            ?: lists.firstOrNull { it.isInbox }?.id
            ?: 0L

        return QuickAddState(
            input = input,
            title = parsed.title,
            dueDate = if (pinnedDay != NO_DATE) LocalDate.ofEpochDay(pinnedDay) else parsed.dueDate,
            dueMinute = if (pinnedMinute != NO_MINUTE) pinnedMinute else parsed.dueMinute,
            listId = listId,
            priority = if (pinnedPriority != 0) pinnedPriority else parsed.priority ?: DEFAULT_PRIORITY,
            recurrence = if (pins.repeat.pinned) pins.repeat.rule else parsed.recurrence,
            // His own spelling, and only while the repeat is the one he typed: a rule that came
            // from the picker has no typed words behind it, so the chip says it in English.
            recurrenceText = if (pins.repeat.pinned) {
                null
            } else {
                parsed.tokens.firstOrNull { it.kind == TokenKind.RECURRENCE }?.text
            },
            tagNames = parsed.tagNames,
            lists = lists,
            canSave = parsed.title.isNotBlank() && listId != 0L,
        )
    }

    /**
     * Writes the task and its tags, clears the draft, and reports what it wrote; null when
     * nothing was written. The write runs in [viewModelScope], so leaving the screen or rotating
     * mid-write cannot cancel it half way. A second call while one is running waits, then finds
     * no draft and returns null. A failed write keeps the draft and returns null.
     */
    suspend fun save(): SavedTask? = viewModelScope.async {
        saveLock.withLock {
            // Read straight from the handle, not [state], which may not have caught up with a clear().
            val lists = listRepository.observeLists().first()
            val current = build(
                savedState[INPUT] ?: "",
                savedState[DATE] ?: NO_DATE,
                savedState[MINUTE] ?: NO_MINUTE,
                Pins(
                    savedState[LIST] ?: NO_LIST,
                    savedState[PRIORITY] ?: 0,
                    RepeatPin.of(savedState[REPEAT] ?: UNPINNED),
                ),
                lists,
            )
            if (!current.canSave) return@withLock null
            val title = current.title.trim()
            try {
                val id = taskRepository.add(
                    title = title,
                    listId = current.listId,
                    dueDate = current.dueDate,
                    dueMinute = current.dueMinute,
                    priority = current.priority,
                    recurrence = current.recurrence,
                )
                tagRepository.attach(id, current.tagNames)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock null
            }
            clear()
            SavedTask(
                title = title,
                dueDate = current.dueDate,
                toInbox = lists.firstOrNull { it.id == current.listId }?.isInbox == true,
            )
        }
    }.await()

    internal companion object {
        const val INPUT = "title"
        const val DATE = "date"
        const val MINUTE = "minute"
        const val LIST = "list"
        const val PRIORITY = "priority"
        const val REPEAT = "repeat"
    }
}
