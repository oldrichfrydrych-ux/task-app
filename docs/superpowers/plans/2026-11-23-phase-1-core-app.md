# Phase 1 · Core app, no sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tasks go into the app, get organised into groups, lists and headings, show up on Today, Upcoming and Inbox, get edited, completed and found again — all on the phone, with nothing talking to Google.

**Architecture:** One `app` module. Room holds everything; DAOs return `Flow`s; a repository per area wraps the DAOs; a ViewModel per screen turns repository flows into screen state. Dependencies are wired by hand in `AppContainer`, held by the Application — no DI library. Navigation Compose carries the graph; the bottom bar from Phase 0 drives the four top-level destinations.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room (with KSP), Navigation Compose, Lifecycle ViewModel Compose, JUnit 4, Compose UI tests.

**Spec:** `docs/superpowers/specs/2026-11-23-phase-1-core-app-design.md` (which argues from `docs/design.md`)

## Global Constraints

- Package `cz.olda.tasks`, minimum SDK 29, one `app` module, Gradle Kotlin DSL.
- Code identifiers in English. Every user-facing string in `res/values/strings.xml`.
- No dynamic colour. **No `INTERNET` permission** — Phase 4 adds it, not this phase.
- Only these libraries may be added, and nothing else without asking Olda: `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (+ the KSP Gradle plugin), `androidx.navigation:navigation-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `androidx.room:room-testing`, `kotlinx-coroutines-test`.
- A task is done only when `gradlew.bat testDebugUnitTest` passes and, with the phone connected, `gradlew.bat connectedDebugAndroidTest` passes.
- **Two devices answer `adb`.** Run device tests with `ANDROID_SERIAL` set to the phone (`adb-FQAUKJPJVOKZVW8H-2NpXZJ._adb-tls-connect._tcp`), and check the Gradle output says `Running tests on devices: CPH2493`. **The phone's screen must be unlocked** or every Compose test fails with "No compose hierarchies found in the app".
- `order` is a SQL keyword: the ordering column is `position` everywhere.
- Colours are stored as `#RRGGBB` strings, never palette indexes.
- Dates are a local epoch day (`Long`); times are minutes since midnight (`Int`); both nullable and independent. Never an instant.
- Phase 0's `AppShellTest` must keep passing: every top-level screen carries `Modifier.testTag("screen-${destination.name}")`, the four tab labels stay on screen, and the add button keeps the content description `"Add task"`.
- Commit at the end of every task and push.

## Review Focus

1. **Midnight passes while the app is open** → Today moves to the new day instead of showing yesterday. Test in Task 6.
2. **A list is deleted while its tasks are on screen** → the tasks move to Inbox, none disappear. Test in Task 8.
3. **Search typed without diacritics or in capitals** ("ZITRA") → finds "zítra". Test in Tasks 1 and 13.
4. **A hex colour typed badly** (`fff`, `#GGGGGG`, empty) → refused with an error, never saved. Tested as a function in Task 1 and in the picker in Task 9.
5. **Largest system font size** → a task row keeps its title, time and list name readable and does not overlap. Test in Task 5.

---

### Task 1: Database foundations

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `build.gradle.kts`
- Create: `app/src/main/java/cz/olda/tasks/data/TextNormaliser.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/theme/HexColor.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/entity/{GroupEntity,ListEntity,HeadingEntity,TagEntity,TaskTagCrossRef,TaskEntity}.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/TextNormaliserTest.kt`
- Test: `app/src/test/java/cz/olda/tasks/ui/theme/HexColorTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/data/AppDatabaseTest.kt`

**Interfaces:**
- Produces:
  - `fun normalise(text: String): String` in `data/TextNormaliser.kt` — lowercased, diacritics stripped.
  - `fun parseHex(input: String): Color?` and `fun Color.toHex(): String` in `ui/theme/HexColor.kt`. `parseHex` accepts `#RRGGBB` and `RRGGBB` in either case and returns null for anything else; `toHex` always returns uppercase `#RRGGBB`. Both are pure functions, and they live here rather than with the colour picker because Task 2 needs `toHex` to seed list colours.
  - `TaskEntity(id: Long = 0, title: String, notes: String = "", titleNormalised: String, notesNormalised: String, listId: Long, headingId: Long? = null, parentTaskId: Long? = null, dueDate: Long? = null, dueMinute: Int? = null, priority: Int = 4, done: Boolean = false, completedAt: Long? = null, position: Int = 0, deletedAt: Long? = null, durationMinutes: Int? = null, goalId: Long? = null, googleId: String? = null, updatedAt: Long = 0)` — table `tasks`.
  - `ListEntity(id: Long = 0, name: String, colorHex: String, icon: String? = null, groupId: Long? = null, position: Int = 0, isInbox: Boolean = false, googleListId: String? = null)` — table `lists`.
  - `GroupEntity(id: Long = 0, name: String, position: Int = 0)` — table `groups`.
  - `HeadingEntity(id: Long = 0, listId: Long, name: String, position: Int = 0)` — table `headings`.
  - `TagEntity(id: Long = 0, name: String, colorHex: String)`; `TaskTagCrossRef(taskId: Long, tagId: Long)`.
  - `abstract class AppDatabase : RoomDatabase()` with `version = 1`, all six entities, and abstract DAO getters added in Tasks 2 and 3.
- The `durationMinutes`, `goalId`, `googleId` and `updatedAt` columns are written by nothing in Phase 1. They exist so Phases 4 and 7 need no migration. Do not drop them.

- [ ] **Step 1: Write the failing tests for both pure functions.**

In `TextNormaliserTest.kt`:

```kotlin
@Test fun stripsCzechDiacriticsAndCase() {
    assertEquals("zitra zavolat na urad", normalise("Zítra zavolat na úřad"))
    assertEquals("prilis zlutoucky kun upel dabelske ody", normalise("Příliš žluťoučký kůň úpěl ďábelské ódy"))
}
@Test fun leavesPlainTextAlone() { assertEquals("call bob", normalise("Call Bob")) }
@Test fun handlesEmptyText() { assertEquals("", normalise("")) }
```

In `HexColorTest.kt`:

```kotlin
@Test fun acceptsWithAndWithoutHash() { assertEquals(Color(0xFF3D6BE0), parseHex("#3d6be0")); assertEquals(Color(0xFF3D6BE0), parseHex("3D6BE0")) }
@Test fun rejectsBadInput()           { listOf("fff", "#GGGGGG", "", "#3D6BE", "#3D6BE0FF").forEach { assertNull(it, parseHex(it)) } }
@Test fun roundTripsThroughHex()      { assertEquals("#3D6BE0", parseHex("#3d6be0")!!.toHex()) }
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest`. Expected: compile errors, unresolved `normalise` and `parseHex`.
- [ ] **Step 3: Add the libraries** from Global Constraints to `gradle/libs.versions.toml`, the KSP plugin to the root `build.gradle.kts`, and Room, Navigation and Lifecycle to `app/build.gradle.kts`. Point Room's schema export at `app/schemas` and add that directory to the source sets so the exported schema is committed.
- [ ] **Step 4: Implement `normalise`** in `data/TextNormaliser.kt` using `java.text.Normalizer` NFD plus a combining-marks regex, then lowercase; and **`parseHex` and `toHex`** in `ui/theme/HexColor.kt`.
- [ ] **Step 5: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Write the failing database test** in `AppDatabaseTest.kt`, using an in-memory database:

```kotlin
@Test fun opensAtVersionOneWithEveryTable() {
    val tables = db.openHelper.readableDatabase
        .query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    listOf("groups", "lists", "headings", "tags", "task_tags", "tasks")
        .forEach { assertTrue("$it missing", tables.contains(it)) }
}
```

- [ ] **Step 7: Run it and watch it fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `AppDatabase`.
- [ ] **Step 8: Implement the six entities and `AppDatabase`** with the signatures above.
- [ ] **Step 9: Run both suites.** Run: `gradlew.bat testDebugUnitTest` and `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`; Phase 0's tests still pass.
- [ ] **Step 10: Commit.**

```bash
git add app/ gradle/ build.gradle.kts
git commit -m "feat: Room database, entities, text normalisation and hex colours"
git push
```

---

### Task 2: Lists, groups and headings

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/dao/{ListDao,GroupDao,HeadingDao}.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/ListRepository.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt` (DAO getters, first-run callback)
- Test: `app/src/androidTest/java/cz/olda/tasks/data/ListRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1's entities and `AppDatabase`.
- Produces `class ListRepository(listDao, groupDao, headingDao)`:
  - `fun observeGroupsWithLists(): Flow<List<GroupWithLists>>`, where `GroupWithLists(group: GroupEntity?, lists: List<ListEntity>)` and a null group holds the ungrouped lists.
  - `fun observeLists(): Flow<List<ListEntity>>` — every list, Inbox first.
  - `suspend fun inboxId(): Long`
  - `suspend fun createList(name: String, colorHex: String? = null, groupId: Long? = null): Long` — **null means "take the next preset"**; a non-null value is stored as given. (Amended during Task 2's review: the original `colorHex: String` left no way to ask for the automatic colour, and a blank-string sentinel gave the same type two meanings across sibling methods.)
  - `suspend fun renameList(id: Long, name: String)`, `suspend fun recolourList(id: Long, colorHex: String)` — `recolourList` requires a real colour and rejects a blank one.
  - `suspend fun deleteList(id: Long)` — **moves the list's tasks to Inbox first**; refuses to delete the Inbox list.
  - `suspend fun createGroup(name: String): Long`, `suspend fun renameGroup(id: Long, name: String)`, `suspend fun deleteGroup(id: Long)` — the group's lists survive with `groupId = null`.
  - `fun observeHeadings(listId: Long): Flow<List<HeadingEntity>>`, `suspend fun createHeading(listId: Long, name: String): Long`, `suspend fun renameHeading(id: Long, name: String)`, `suspend fun deleteHeading(id: Long)` — the heading's tasks stay in the list with `headingId = null`.

- [ ] **Step 1: Write the failing tests** in `ListRepositoryTest.kt` against an in-memory database:

```kotlin
@Test fun firstRunSeedsExactlyOneInbox()        // observeLists().first() has one list, isInbox, named from R.string.inbox
@Test fun inboxCannotBeDeleted()                // deleteList(inboxId()) leaves it present
@Test fun deletingAListMovesItsTasksToInbox()   // 2 tasks in a list; after deleteList, both have listId == inboxId() and neither is soft-deleted
@Test fun deletingAGroupKeepsItsLists()         // list.groupId == null afterwards, list still present
@Test fun deletingAHeadingKeepsItsTasks()       // task.headingId == null afterwards
@Test fun newListTakesTheNextPresetColour()     // first created list gets ListPalette[1].light as #RRGGBB, Inbox having taken [0]
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `ListRepository`.
- [ ] **Step 3: Implement the three DAOs, `ListRepository`, and a `RoomDatabase.Callback` that seeds Inbox on create.** Seed Inbox with `ListPalette[0]`; `createList` picks the next preset by `position % ListPalette.size`. Colour values come from `ui/theme/ListPalette.kt` (Phase 0) formatted as `#RRGGBB`.
- [ ] **Step 4: Run the tests and watch them pass.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`, 6 new tests pass.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: lists, groups and headings, with Inbox seeded on first run"
git push
```

---

### Task 3: Tasks

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/dao/TaskDao.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/TaskRepository.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/TaskWithList.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/data/TaskRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1's entities, Task 2's `ListRepository.inboxId()`.
- Produces `data class TaskWithList(@Embedded val task: TaskEntity, val listName: String, val listColorHex: String)`.
- Produces `class TaskRepository(taskDao: TaskDao, clock: java.time.Clock)`:
  - `fun observeDay(day: LocalDate): Flow<DaySections>`, where `DaySections(overdue: List<TaskWithList>, timed: List<TaskWithList>, anytime: List<TaskWithList>, tomorrow: List<TaskWithList>)`. `overdue` and `tomorrow` are empty unless `day` is today. `timed` is ordered by `dueMinute`, `anytime` by `position`.
  - `fun observeUpcoming(): Flow<List<UpcomingDay>>`, where `UpcomingDay(date: LocalDate, tasks: List<TaskWithList>)`, days after today, earliest first.
  - `fun observeInList(listId: Long): Flow<List<TaskWithList>>`
  - `fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>`
  - `fun observeTask(id: Long): Flow<TaskEntity?>`
  - `fun observeDots(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, List<String>>>` — the distinct list colours that have a task on each day, for the week strip.
  - `fun search(query: String): Flow<List<TaskWithList>>` — matches `titleNormalised` or `notesNormalised` against `normalise(query)`.
  - `fun observeOpenCounts(): Flow<Map<Long, Int>>` — open, undeleted tasks per list id, for the counts on the Lists screen in Task 8.
  - `suspend fun add(title: String, listId: Long, dueDate: LocalDate?, dueMinute: Int?, priority: Int, parentTaskId: Long? = null, headingId: Long? = null): Long` — fills both normalised columns and appends by `position`.
  - `suspend fun update(task: TaskEntity)` — refreshes the normalised columns.
  - `suspend fun setDone(id: Long, done: Boolean)` — **completing also completes open subtasks**; reopening touches only the task itself.
  - `suspend fun softDelete(id: Long)`, `suspend fun restore(id: Long)`
- Every query filters out rows with a non-null `deletedAt`.

- [ ] **Step 1: Write the failing tests** in `TaskRepositoryTest.kt`, with a fixed `Clock`:

```kotlin
@Test fun dayPutsOverdueFirstThenTimedThenAnytime()   // one task due yesterday, two due today at 15:00 and 09:00, one due today with no time
@Test fun overdueAndTomorrowAreEmptyForOtherDays()    // observeDay(today.plusDays(3)) has empty overdue and tomorrow
@Test fun upcomingGroupsByDayEarliestFirst()
@Test fun completingAParentCompletesItsSubtasks()
@Test fun reopeningAParentLeavesSubtasksDone()
@Test fun softDeletedTasksLeaveEveryQuery()
@Test fun searchIgnoresCaseAndDiacritics()            // add("Zítra zavolat na úřad"); search("ZITRA ZAVOLAT") finds it
@Test fun dotsReportOneColourPerListPerDay()
@Test fun openCountsIgnoreDoneAndDeletedTasks()
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `TaskRepository`.
- [ ] **Step 3: Implement `TaskDao`, `TaskWithList` and `TaskRepository`** with the signatures above. The DAO returns rows; the repository does the day arithmetic with `LocalDate.now(clock).toEpochDay()` and the grouping.
- [ ] **Step 4: Run the tests and watch them pass.** Run: `gradlew.bat connectedDebugAndroidTest` and `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: task storage, queries and completion rules"
git push
```

---

### Task 4: Wiring and navigation

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/AppContainer.kt`, `TasksApplication.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/nav/AppNavHost.kt`
- Modify: `app/src/main/AndroidManifest.xml` (`android:name=".TasksApplication"`)
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/nav/AppNavHostTest.kt`

**Interfaces:**
- Consumes: Tasks 2 and 3's repositories.
- Produces:
  - `class AppContainer(context: Context)` with `val database: AppDatabase`, `val listRepository: ListRepository`, `val taskRepository: TaskRepository`.
  - `class TasksApplication : Application()` with `val container: AppContainer`.
  - `object AppViewModelProvider { val Factory: ViewModelProvider.Factory }` — built with `viewModelFactory { initializer { … } }`; every later task adds its ViewModel here.
  - `AppNavHost(navController: NavHostController, selected: Destination, modifier: Modifier)` — the four top-level routes plus `list/{listId}`, `task/{taskId}` and `search`.
  - `AppShell` keeps its own `rememberSaveable` selected tab and hands it to `AppNavHost`; tapping a tab navigates and pops to that tab's start.
  - `AppShell`'s `Scaffold` gains a `SnackbarHost`, and `val LocalSnackbarHostState: ProvidableCompositionLocal<SnackbarHostState>` is provided around the nav host, so any screen can post a message without owning a host. Task 5 builds the undo on top of it.
- Each top-level screen keeps `Modifier.testTag("screen-${destination.name}")`, so Phase 0's `AppShellTest` keeps passing unchanged.

- [ ] **Step 1: Write the failing tests** in `AppNavHostTest.kt`:

```kotlin
@Test fun opensOnToday()                       // onNodeWithTag("screen-Today").assertIsDisplayed()
@Test fun aTabTakesYouToItsScreen()            // tap "Lists", onNodeWithTag("screen-Lists").assertIsDisplayed()
@Test fun backFromADetailReturnsToItsTab()     // navigate to a list detail, pressBack, screen-Lists is shown again
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `AppNavHost`.
- [ ] **Step 3: Implement `AppContainer`, `TasksApplication`, `AppViewModelProvider`, `AppNavHost`**, register the application class in the manifest, and change `AppShell` to host the nav graph instead of a single `PlaceholderScreen`. Screens that later tasks build stay as `PlaceholderScreen` for now, each carrying its own test tag.
- [ ] **Step 4: Run both suites.** Run: `gradlew.bat testDebugUnitTest` and `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`; Phase 0's six `AppShellTest` tests still pass.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: hand-wired dependencies and the navigation graph"
git push
```

---

### Task 5: The task row

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/components/TaskRow.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/components/TaskListSection.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/components/TaskRowTest.kt`

**Interfaces:**
- Consumes: `TaskWithList` from Task 3; `ListPalette` and `AppTheme` from Phase 0.
- Produces:
  - `@Composable fun TaskRow(item: TaskWithList, onToggleDone: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier)` — the compact rail from *Look*: start time on the left, title in the middle, list name on the right in the list's colour, a checkbox with content description `R.string.complete_task`. Root carries `testTag("task-${item.task.id}")`.
  - `@Composable fun TaskListSection(title: String?, items: List<TaskWithList>, onToggleDone: (Long) -> Unit, onClick: (Long) -> Unit, modifier: Modifier = Modifier)` — a header and its rows; renders nothing when `items` is empty.
- Strings: `complete_task` "Complete task", `section_anytime` "Anytime", `section_overdue` "Overdue", `section_tomorrow` "Tomorrow".
- This task defines the row and the section only. The shared "completed, with undo" behaviour arrives in Task 6, where it is first used and tested.

- [ ] **Step 1: Write the failing tests** in `TaskRowTest.kt`:

```kotlin
@Test fun showsTitleTimeAndListName()
@Test fun undatedRowShowsNoTime()
@Test fun tappingTheCheckboxReportsDone()                 // onNodeWithContentDescription("Complete task").performClick()
@Test fun anEmptySectionDrawsNothing()                    // TaskListSection(title = "Overdue", items = emptyList()); onNodeWithText("Overdue").assertDoesNotExist()
@Test fun rowSurvivesTheLargestFontSize() {               // Review Focus 5
    rule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
            AppTheme { TaskRow(sample, {}, {}) }
        }
    }
    rule.onNodeWithText(sample.task.title).assertIsDisplayed()
    rule.onNodeWithText(sample.listName).assertIsDisplayed()
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `TaskRow`.
- [ ] **Step 3: Implement `TaskRow` and `TaskListSection`.** At large font sizes the row grows rather than clipping: no fixed heights, the title takes the remaining width with `maxLines = 2`.
- [ ] **Step 4: Run the tests and watch them pass.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: the shared task row and list section"
git push
```

---

### Task 6: Today

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/today/{TodayScreen,TodayViewModel,WeekStrip}.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/test/java/cz/olda/tasks/ui/today/TodayViewModelTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/today/TodayScreenTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.observeDay`, `observeDots` (Task 3); `TaskListSection` and `rememberCompletionUndo` (Task 5).
- Produces:
  - `class TodayViewModel(repository: TaskRepository, clock: Clock) : ViewModel()` with `val state: StateFlow<TodayState>`, `fun showDay(date: LocalDate)`, `fun toggleDone(id: Long)`. `TodayState(shownDay: LocalDate, isToday: Boolean, sections: DaySections, dots: Map<LocalDate, List<String>>, weekStart: LocalDate)`.
  - `@Composable fun WeekStrip(weekStart: LocalDate, shownDay: LocalDate, dots: Map<LocalDate, List<String>>, onPickDay: (LocalDate) -> Unit, modifier: Modifier = Modifier)` — seven days, each with its list-colour dots; the shown day is marked; each day carries `testTag("day-${date}")`.
  - `@Composable fun TodayScreen(viewModel: TodayViewModel, onOpenTask: (Long) -> Unit, modifier: Modifier = Modifier)` — root carries `testTag("screen-Today")`.
  - `@Composable fun rememberCompletionUndo(onSetDone: (Long, Boolean) -> Unit): (Long) -> Unit` in `ui/components/CompletionUndo.kt` — the single place the "completed, with undo" behaviour lives. It marks the task done, posts a snackbar on `LocalSnackbarHostState` (Task 4) with `R.string.task_completed` and `R.string.undo`, and reopens the task if the action is tapped. **Every later screen that shows tasks uses this rather than calling `setDone` directly**, so the behaviour is written once. `completingARowRemovesItWithAnUndo` below is its test.
- Strings: `task_completed` "Task completed", `undo` "Undo".
- The header names the shown day; it reads from `R.string.today` when the shown day is today.

- [ ] **Step 1: Write the failing ViewModel tests** in `TodayViewModelTest.kt`, with a mutable fake `Clock`:

```kotlin
@Test fun startsOnToday()
@Test fun pickingADayDropsOverdueAndTomorrow()        // showDay(today.plusDays(2)); state.isToday is false, sections.overdue is empty
@Test fun midnightMovesTheShownDay() {                // Review Focus 1
    val clock = MutableClock(startOfDay("2026-11-23T23:59"))
    // state.shownDay is 23 Nov; advance the clock past midnight and re-collect
    clock.advanceBy(Duration.ofMinutes(2))
    assertEquals(LocalDate.parse("2026-11-24"), vm.state.value.shownDay)
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest`. Expected: compile error, unresolved `TodayViewModel`.
- [ ] **Step 3: Implement `TodayViewModel`.** For the midnight case the ViewModel re-reads the date from the clock on a ticker that fires at the next local midnight, and re-emits when the day it is showing was today.
- [ ] **Step 4: Run them and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Write the failing screen tests** in `TodayScreenTest.kt`:

```kotlin
@Test fun showsOverdueThenTimedThenAnytime()
@Test fun tappingADayInTheStripShowsThatDay()      // onNodeWithTag("day-2026-11-25").performClick()
@Test fun theTomorrowRowOnlyShowsOnToday()
@Test fun completingARowRemovesItWithAnUndo()      // tap the checkbox, the row goes, "Undo" is on screen, tapping it brings the row back
```

- [ ] **Step 6: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `TodayScreen`.
- [ ] **Step 7: Implement `WeekStrip` and `TodayScreen`,** register `TodayViewModel` in `AppViewModelProvider`, and replace the Today placeholder in `AppNavHost`.
- [ ] **Step 8: Run both suites.** Run: `gradlew.bat testDebugUnitTest` and `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 9: Commit.**

```bash
git add app/
git commit -m "feat: Today with the week strip and its sections"
git push
```

---

### Task 7: Upcoming and Inbox

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/upcoming/{UpcomingScreen,UpcomingViewModel}.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/inbox/{InboxScreen,InboxViewModel}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/upcoming/UpcomingScreenTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/inbox/InboxScreenTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.observeUpcoming`, `observeInList` (Task 3); `ListRepository.inboxId()` (Task 2); `TaskListSection` and `rememberCompletionUndo` (Task 5).
- Produces:
  - `class UpcomingViewModel(repository: TaskRepository) : ViewModel()` with `val state: StateFlow<List<UpcomingDay>>`, `fun toggleDone(id: Long)`.
  - `@Composable fun UpcomingScreen(viewModel: UpcomingViewModel, onOpenTask: (Long) -> Unit, modifier: Modifier = Modifier)` — one section per day, the date as its header; root `testTag("screen-Upcoming")`.
  - `class InboxViewModel(taskRepository, listRepository) : ViewModel()` with `val state: StateFlow<List<TaskWithList>>`, `fun toggleDone(id: Long)`.
  - `@Composable fun InboxScreen(viewModel: InboxViewModel, onOpenTask: (Long) -> Unit, modifier: Modifier = Modifier)` — root `testTag("screen-Inbox")`.
- Strings: `upcoming_empty` "Nothing coming up", `inbox_empty` "Inbox is empty".

- [ ] **Step 1: Write the failing tests:**

```kotlin
// UpcomingScreenTest
@Test fun groupsTasksUnderTheirDay()
@Test fun todaysTasksDoNotAppear()
@Test fun emptyUpcomingSaysSo()
// InboxScreenTest
@Test fun showsOnlyInboxTasks()
@Test fun emptyInboxSaysSo()
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile errors for both screens.
- [ ] **Step 3: Implement both ViewModels and both screens,** register them, and replace their placeholders.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: Upcoming and Inbox"
git push
```

---

### Task 8: Lists, groups and their editing

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/lists/{ListsScreen,ListsViewModel,ListEditSheet}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/lists/ListsScreenTest.kt`

**Interfaces:**
- Consumes: every mutating method on `ListRepository` (Task 2).
- Produces:
  - `class ListsViewModel(listRepository: ListRepository, taskRepository: TaskRepository) : ViewModel()` with `val state: StateFlow<List<GroupWithLists>>` and `createList`, `renameList`, `recolourList`, `deleteList`, `createGroup`, `renameGroup`, `deleteGroup`.
  - `@Composable fun ListsScreen(viewModel: ListsViewModel, onOpenList: (Long) -> Unit, modifier: Modifier = Modifier)` — groups as headers, lists as rows with their colour dot and open-task count; root `testTag("screen-Lists")`.
  - `@Composable fun ListEditSheet(initial: ListEntity?, onSave: (name: String, colorHex: String, groupId: Long?) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit)` — the name field carries `testTag("list-name-field")`. **In this task the colour is shown as a read-only dot and passed through unchanged**; a new list keeps the preset `ListRepository.createList` gave it. Task 9 makes it editable by dropping `ColorPicker` into this sheet, which is why the signature already carries `colorHex`.
- Inbox appears in neither the Lists screen nor the group picker.

- [ ] **Step 1: Write the failing tests** in `ListsScreenTest.kt`:

```kotlin
@Test fun showsGroupsWithTheirLists()
@Test fun ungroupedListsAppearWithoutAGroupHeader()
@Test fun inboxIsNotListedHere()
@Test fun aListRowCountsItsOpenTasks()
@Test fun deletingAListMovesItsTasksToInbox() {    // Review Focus 2
    // a list holding two tasks is deleted from the sheet; the confirmation says so,
    // and the Inbox screen then shows both tasks
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: compile error, unresolved `ListsScreen`.
- [ ] **Step 3: Implement `ListsViewModel`, `ListsScreen` and `ListEditSheet`.** Deleting asks for confirmation and says where the tasks go, using `R.string.delete_list_moves_tasks`.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: the Lists screen with groups and list editing"
git push
```

---

### Task 9: The colour picker

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/components/ColorPicker.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/settings/SettingsScreen.kt`
- Modify: `ui/lists/ListEditSheet.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/components/ColorPickerTest.kt`

**Interfaces:**
- Consumes: `ListPalette`, `contrastRatio` (Phase 0); `parseHex`, `toHex` (Task 1).
- Produces:
  - `@Composable fun ColorPicker(selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier)` — the 12 preset swatches plus a Custom field (`testTag("hex-field")`); shows the colour on a light and a dark preview card, and warns without blocking when text in it would fall below 4.5:1.
  - `@Composable fun SettingsScreen(modifier: Modifier = Modifier)` — root `testTag("screen-Settings")`; in Phase 1 it holds the colour section only.

- [ ] **Step 1: Write the failing picker tests** in `ColorPickerTest.kt`:

```kotlin
@Test fun showsTwelvePresets()
@Test fun pickingAPresetReportsItsHex()
@Test fun badHexShowsAnErrorAndSavesNothing()   // Review Focus 4: type "#GGGGGG", the error shows, onPick was never called
@Test fun lowContrastWarnsButStillSaves()
```

- [ ] **Step 2: Run them and watch them fail.** Expected: compile error, unresolved `ColorPicker`.
- [ ] **Step 3: Implement `ColorPicker` and `SettingsScreen`,** and use the picker from `ListEditSheet`.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: the colour picker with presets and custom hex"
git push
```

---

### Task 10: A list and its headings

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/lists/{ListDetailScreen,ListDetailViewModel}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/lists/ListDetailScreenTest.kt`

**Interfaces:**
- Consumes: `ListRepository.observeHeadings`, `createHeading`, `renameHeading`, `deleteHeading` (Task 2); `TaskRepository.observeInList` (Task 3); `TaskListSection` and `rememberCompletionUndo` (Task 5).
- Produces:
  - `class ListDetailViewModel(listId: Long, listRepository, taskRepository) : ViewModel()` with `val state: StateFlow<ListDetailState>`, where `ListDetailState(list: ListEntity?, unheaded: List<TaskWithList>, sections: List<HeadingSection>)` and `HeadingSection(heading: HeadingEntity, tasks: List<TaskWithList>)`.
  - `@Composable fun ListDetailScreen(viewModel: ListDetailViewModel, onOpenTask: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)` — root `testTag("screen-ListDetail")`.

- [ ] **Step 1: Write the failing tests** in `ListDetailScreenTest.kt`:

```kotlin
@Test fun tasksWithoutAHeadingComeFirst()
@Test fun eachHeadingShowsItsTasks()
@Test fun addingAHeadingShowsIt()
@Test fun deletingAHeadingKeepsItsTasksInTheList()
```

- [ ] **Step 2: Run them and watch them fail.** Expected: compile error, unresolved `ListDetailScreen`.
- [ ] **Step 3: Implement the ViewModel and the screen,** register them, and wire `onOpenList` from Task 8 to the `list/{listId}` route.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: a list with its headings"
git push
```

---

### Task 11: Task detail and subtasks

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/task/{TaskDetailScreen,TaskDetailViewModel}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/nav/AppNavHost.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/task/TaskDetailScreenTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.observeTask`, `observeSubtasks`, `update`, `setDone`, `softDelete`, `add` (Task 3); `ListRepository.observeLists` (Task 2).
- Produces:
  - `class TaskDetailViewModel(taskId: Long, taskRepository, listRepository) : ViewModel()` with `val state: StateFlow<TaskDetailState>` and `setTitle`, `setNotes`, `setList`, `setHeading`, `setDueDate`, `setDueMinute`, `setPriority`, `toggleDone`, `addSubtask`, `delete`.
  - `@Composable fun TaskDetailScreen(viewModel: TaskDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier)` — title field `testTag("task-title-field")`, notes `testTag("task-notes-field")`; root `testTag("screen-TaskDetail")`.
- A subtask row offers no "add subtask" of its own: one level only.

- [ ] **Step 1: Write the failing tests** in `TaskDetailScreenTest.kt`:

```kotlin
@Test fun showsEveryFieldOfTheTask()
@Test fun editingTheTitleSaves()
@Test fun addingASubtaskShowsItUnderTheTask()
@Test fun completingTheParentChecksItsSubtasks()
@Test fun aSubtaskCannotTakeASubtask()
@Test fun deletingTheTaskGoesBack()
```

- [ ] **Step 2: Run them and watch them fail.** Expected: compile error, unresolved `TaskDetailScreen`.
- [ ] **Step 3: Implement the ViewModel and the screen,** register them, and open it from every `TaskRow` via the `task/{taskId}` route.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: task detail with subtasks"
git push
```

---

### Task 12: Quick-add with chips

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/quickadd/{QuickAddViewModel,QuickAddChips}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/AppShell.kt`, `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.add` (Task 3); `ListRepository.observeLists`, `inboxId` (Task 2). The list chip colours itself from that list's own `colorHex`; it does not use the colour picker.
- Produces:
  - `class QuickAddViewModel(taskRepository, listRepository) : ViewModel()` with `val state: StateFlow<QuickAddState>`, where `QuickAddState(title: String, dueDate: LocalDate?, dueMinute: Int?, listId: Long, priority: Int, lists: List<ListEntity>, canSave: Boolean)`, and `setTitle`, `setDate`, `setList`, `setPriority`, `suspend fun save()`.
  - `@Composable fun QuickAddChips(state: QuickAddState, onPickDate: () -> Unit, onPickList: () -> Unit, onPickPriority: () -> Unit, modifier: Modifier = Modifier)` — three chips with `testTag("chip-date")`, `"chip-list"`, `"chip-priority"`, each showing its current value.
  - `QuickAddSheet(onDismiss: () -> Unit)` keeps its signature, its `testTag("quick-add-field")` and its focus-on-open behaviour, so Phase 0's tests keep passing.
- Defaults: no date, the Inbox list, priority 4. `canSave` is false while the title is blank.

- [ ] **Step 1: Write the failing tests** in `QuickAddSheetTest.kt`:

```kotlin
@Test fun chipsStartAtNoDateInboxAndPriorityFour()
@Test fun pickingADateShowsItOnTheChip()
@Test fun pickingAListShowsItsNameOnTheChip()
@Test fun savingWritesTheTaskWithItsChips()        // the task lands in the chosen list with the chosen date and priority
@Test fun blankTitleCannotBeSaved()
@Test fun savingClosesTheSheet()
```

- [ ] **Step 2: Run them and watch them fail.** Expected: compile error, unresolved `QuickAddChips`.
- [ ] **Step 3: Implement `QuickAddViewModel` and `QuickAddChips`, and extend `QuickAddSheet`** with the chip row and a save action. Do not change the field's test tag or its focus behaviour.
- [ ] **Step 4: Run both suites.** Run: `gradlew.bat testDebugUnitTest` and `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`; Phase 0's `addButtonOpensFocusedQuickAdd`, `backClosesQuickAdd` and `quickAddKeepsWhatYouTyped` still pass.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: quick-add with date, list and priority chips"
git push
```

---

### Task 13: Search

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/search/{SearchScreen,SearchViewModel}.kt`
- Modify: `ui/AppViewModelProvider.kt`, `ui/lists/ListsScreen.kt` (the search icon in its header), `res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/search/SearchScreenTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.search` (Task 3); `TaskListSection` and `rememberCompletionUndo` (Task 5).
- Produces:
  - `class SearchViewModel(repository: TaskRepository) : ViewModel()` with `val state: StateFlow<SearchState>`, where `SearchState(query: String, results: List<TaskWithList>)`, and `fun setQuery(query: String)`.
  - `@Composable fun SearchScreen(viewModel: SearchViewModel, onOpenTask: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)` — the field carries `testTag("search-field")`; root `testTag("screen-Search")`.
- Reached from a search icon in the Lists screen header and the `search` route.

- [ ] **Step 1: Write the failing tests** in `SearchScreenTest.kt`:

```kotlin
@Test fun findsByTitle()
@Test fun findsByNotes()
@Test fun ignoresCaseAndDiacritics()      // Review Focus 3: a task "Zítra zavolat na úřad" is found by "ZITRA ZAVOLAT"
@Test fun anEmptyQueryShowsNoResults()
@Test fun doneAndDeletedTasksDoNotAppear()
```

- [ ] **Step 2: Run them and watch them fail.** Expected: compile error, unresolved `SearchScreen`.
- [ ] **Step 3: Implement the ViewModel and the screen,** register them, and add the entry point.
- [ ] **Step 4: Run both suites.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.**

```bash
git add app/
git commit -m "feat: search across titles and notes"
git push
```

---

### Task 14: A week of real use

**Files:** none, unless the week turns something up.

- [ ] **Step 1 [Olda]: Install the app** and use it daily beside TickTick for a week: capture tasks as they come, work from Today, organise into lists.
- [ ] **Step 2 [Olda]: Keep a note** of anything that was slower than TickTick, anything you expected and did not find, and anything that looked wrong.
- [ ] **Step 3: Triage the note together** into what Phase 1 should fix now, what belongs to a later phase, and what was a misunderstanding of the design.

**Phase 1 is done when** Olda has used the app daily for a week, both test commands pass, and the fixes from that week's note are in.
