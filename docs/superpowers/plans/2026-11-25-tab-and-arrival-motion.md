# Tab travel and task arrival Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make switching tabs travel, and make it visible where a saved task went by flying a pill from the add button, over the bar, onto the tab that holds it.

**Architecture:** `save()` starts reporting what it wrote; a pure function turns that into a destination tab; the shell draws a pill above the whole `Scaffold` and moves it there by animated offset, then pulses the tab. Tab transitions move to a shared axis so siblings stop looking like depth.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 1.4.0, `androidx.compose.animation` 1.10.4, the six expressive springs already in `AppMotion`. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-25-tab-and-arrival-motion-design.md`

## Global Constraints

- No new libraries and no dependency version changes (CLAUDE.md: ask Olda first).
- No `@Entity` changes: the schema is version 1 with no destructive-migration fallback and Olda's real tasks are in it.
- User-facing text only in `res/values/strings.xml`; code identifiers in English.
- `data/` stays free of `android.*` imports and `Context`, guarded by `DataLayerIsPlatformFreeTest`.
- Every animation collapses to nothing when `LocalAnimationsEnabled` is false.
- Capture must never get slower: the flight starts after the write and is cancelled by the next tap on the add button.
- The flight moves by animated **offset**, never by a `graphicsLayer` transform: a transform is invisible to the semantics tree and this project has already deleted two tests that could not fail for that reason.
- Device tests run only on the phone: `ANDROID_SERIAL="adb-FQAUKJPJVOKZVW8H-2NpXZJ._adb-tls-connect._tcp"`, awake and unlocked. Back up `tasks.db` first — `connectedDebugAndroidTest` uninstalls the app and takes the database with it.

## Review Focus

1. **Animations off.** No pill, no pulse, and tabs change without travel. Tests in Tasks 3 and 4.
2. **Two saves in quick succession.** The second pill replaces the first; there is never more than one in flight and the first does not leave a ghost. Test in Task 4.
3. **Tapping + while a pill is flying.** The flight is cancelled and quick-add still opens focused on the first frame. Test in Task 4.
4. **Saving from a screen that is not a tab** — a task detail, a list, search. The pill still flies to the right tab, and the tab the shell shows as selected does not change. Test in Task 4.
5. **A long Czech title in the pill.** It stays a pill on one line, clipped or ellipsised, not a paragraph that covers the screen. Test in Task 4.

---

### Task 1: `save()` reports what it wrote

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddViewModel.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt:73-78`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class SavedTask(val title: String, val dueDate: LocalDate?, val toInbox: Boolean)` in `QuickAddViewModel.kt`. `title` is the trimmed title as written; `toInbox` is true when the chosen list is the Inbox row.
  - `suspend fun QuickAddViewModel.save(): SavedTask?` — null when nothing was written, replacing the old `Boolean`.
  - `QuickAddSheet(onDismiss: () -> Unit, onSaved: (SavedTask) -> Unit = {}, viewModel: QuickAddViewModel = …)`. `onSaved` runs before `onDismiss`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun saveReportsTheTaskItWrote() {
    val vm = QuickAddViewModel(tasks, lists)
    vm.setTitle("  Zavolat na urad  ")
    vm.setList(work)
    val day = LocalDate.now().plusDays(2)
    vm.setDate(day)
    val saved = runBlocking { vm.save() }
    assertEquals(SavedTask("Zavolat na urad", day, toInbox = false), saved)
}

@Test fun saveReportsInboxWhenThatIsWhereItWent() {
    val vm = QuickAddViewModel(tasks, lists)
    vm.setTitle("Bez data")
    val saved = runBlocking { vm.save() }
    assertEquals(SavedTask("Bez data", null, toInbox = true), saved)
}

@Test fun saveReportsNothingWhenItWroteNothing() {
    val vm = QuickAddViewModel(tasks, lists)
    assertNull(runBlocking { vm.save() })
}
```

Rewrite the existing `twoSavesAtOnceWriteOneTask`: `results.sorted()` no longer compiles. Assert instead that exactly one of the two results is non-null and one task exists.

- [ ] **Step 2: Run them and watch them fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.quickadd.QuickAddSheetTest`
Expected: FAIL, unresolved reference `SavedTask`.

- [ ] **Step 3: Return `SavedTask?` from `save()`**

The lists are already loaded inside `save()` to build the current state; the Inbox row is the one with `isInbox`. Keep every other guarantee the KDoc promises — the lock, the single write, the draft kept on failure.

- [ ] **Step 4: Thread `onSaved` through the sheet**

`QuickAddSheet`'s local `save()` calls `onSaved(saved)` then `onDismiss()` when the result is non-null, and does nothing when it is null.

- [ ] **Step 5: Run the class and the shell class**

Run: as Step 2, plus `cz.olda.tasks.ui.AppShellTest`.
Expected: PASS, including `aSavedTaskDoesNotComeBackInTheNextQuickAdd`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/quickadd app/src/androidTest/java/cz/olda/tasks/ui/quickadd
git commit -m "feat: quick-add reports the task it saved"
```

---

### Task 2: Where a saved task belongs

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/ArrivalTab.kt`
- Test: `app/src/test/java/cz/olda/tasks/ui/ArrivalTabTest.kt`

**Interfaces:**
- Consumes: `SavedTask` from Task 1.
- Produces: `fun arrivalTab(saved: SavedTask, today: LocalDate): Destination`.

A pure function, so it runs in the JVM suite with no device and no clock.

- [ ] **Step 1: Write the failing test**

```kotlin
class ArrivalTabTest {
    private val today = LocalDate.of(2026, 11, 25)
    private fun saved(date: LocalDate?, toInbox: Boolean = true) =
        SavedTask("Zavolat na urad", date, toInbox)

    @Test fun aTaskForTodayGoesToToday() =
        assertEquals(Destination.Today, arrivalTab(saved(today), today))

    /** Overdue tasks are listed on Today, under Overdue - not on Upcoming. */
    @Test fun anOverdueTaskGoesToToday() =
        assertEquals(Destination.Today, arrivalTab(saved(today.minusDays(3)), today))

    @Test fun aLaterTaskGoesToUpcoming() =
        assertEquals(Destination.Upcoming, arrivalTab(saved(today.plusDays(1)), today))

    @Test fun anUndatedTaskInInboxGoesToInbox() =
        assertEquals(Destination.Inbox, arrivalTab(saved(null), today))

    @Test fun anUndatedTaskInSomeOtherListGoesToLists() =
        assertEquals(Destination.Lists, arrivalTab(saved(null, toInbox = false), today))
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `gradlew.bat testDebugUnitTest --tests "cz.olda.tasks.ui.ArrivalTabTest"`
Expected: FAIL, unresolved reference `arrivalTab`.

- [ ] **Step 3: Write `arrivalTab`**

A KDoc saying these four rules mirror what the screens actually query, so the pill cannot land on a tab that does not show the task.

- [ ] **Step 4: Run the whole unit suite**

Run: `gradlew.bat testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/ArrivalTab.kt app/src/test/java/cz/olda/tasks/ui/ArrivalTabTest.kt
git commit -m "feat: work out which tab a saved task lands on"
```

---

### Task 3: Tab content travels on a shared axis

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/nav/AppNavHost.kt:56-92`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/nav/AppNavHostTest.kt`

**Interfaces:**
- Consumes: `AppMotion.spatial`, `AppMotion.effects`, `LocalAnimationsEnabled`.
- Produces: no signature change.

Both screens travel the **same short distance**, a fraction of the width rather than a full throw, and cross-fade through each other. Direction comes from comparing the two routes' positions in `Destination.entries`. The existing non-tab push — in from the trailing edge, the screen behind stepping back a quarter — is untouched, and the test below is what keeps the two readable as different things.

- [ ] **Step 1: Write the failing tests**

```kotlin
/** Siblings move; the cross-fade said nothing about which way. */
@Test fun aTabToTheRightArrivesFromTheRight() {
    rule.mainClock.autoAdvance = false
    graph { nav -> rule.runOnUiThread { nav.navigate(Destination.Lists.route) } }
    rule.mainClock.advanceTimeByFrame(); rule.mainClock.advanceTimeByFrame()
    val arriving = rule.onNodeWithTag("screen-Lists").getUnclippedBoundsInRoot()
    assertTrue("Lists did not arrive from the right: left=${arriving.left}", arriving.left > 1.dp)
}

/** Going back along the bar comes from the other side. */
@Test fun aTabToTheLeftArrivesFromTheLeft()

/** A sibling is not a push: tabs travel a shorter distance than opening a detail does. */
@Test fun aTabTravelsLessFarThanOpeningADetail()

/** Review Focus 1. */
@Test fun tabsChangeWithoutTravelWhenAnimationsAreOff()
```

- [ ] **Step 2: Run them and watch them fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.nav.AppNavHostTest`
Expected: FAIL — today both tabs cross-fade, so `left` is 0 in all three travel tests.

- [ ] **Step 3: Replace the between-tabs branches**

In all four transitions. With `LocalAnimationsEnabled` false they stay `EnterTransition.None` / `ExitTransition.None`.

- [ ] **Step 4: Run the nav and shell classes**

Expected: PASS, including `tabsDoNotPileUpOnTheBackStack` and `aDetailKeepsTheTabItWasOpenedFrom`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/nav/AppNavHost.kt app/src/androidTest/java/cz/olda/tasks/ui/nav/AppNavHostTest.kt
git commit -m "feat: tabs travel sideways instead of cross-fading"
```

---

### Task 4: The pill flies to its tab

The largest task, and the one carrying four of the five Review Focus items.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/components/TaskFlight.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt`
- Create: `app/src/androidTest/java/cz/olda/tasks/ui/components/TaskFlightTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt`

**Interfaces:**
- Consumes: `SavedTask` (Task 1), `arrivalTab` (Task 2), `AppMotion.spatial`, `LocalAnimationsEnabled`.
- Produces:
  - `TaskFlight(label: String, from: Rect, to: Rect, onFinished: () -> Unit, modifier: Modifier = Modifier)` in `ui/components/TaskFlight.kt`, tagged `"task-flight"`. `from` and `to` are in root coordinates. It positions itself with `Modifier.offset { … }` from a `0f..1f` progress animated on `AppMotion.spatial()`, rising `FlightArc` above the straight line at the midpoint so the motion clears the bar, and calls `onFinished` once when it settles. With animations off it composes nothing at all.
  - `private val FlightArc = 160.dp` in the same file — the number Olda tunes first.
  - `TabStrip(…, onTabBounds: (Destination, Rect) -> Unit)` and `AddButton(…, onBounds: (Rect) -> Unit)` in `AppShell.kt`, both reporting `coordinates.boundsInRoot()`. `TabStrip` keeps its existing parent-relative measurement for the pill; these are a second, separate report.

- [ ] **Step 1: Write the failing tests for the flight itself**

```kotlin
class TaskFlightTest {
    @Test fun itStartsAtTheSourceAndEndsAtTheTarget()
    @Test fun itRisesAboveTheStraightLineOnTheWay()   // clears the bar
    /** Review Focus 5. */
    @Test fun aLongCzechTitleStaysOnePill()           // "zkontrolovat preobjednavku dokumentace", one line, width bounded
    /** Review Focus 1. */
    @Test fun nothingIsDrawnWithAnimationsOff()
    @Test fun itReportsFinishedOnceAndOnlyOnce()
}
```

Drive the clock by hand; read `onNodeWithTag("task-flight").getUnclippedBoundsInRoot()`.

- [ ] **Step 2: Run them and watch them fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.components.TaskFlightTest`
Expected: FAIL, unresolved reference `TaskFlight`.

- [ ] **Step 3: Write `TaskFlight`**

- [ ] **Step 4: Run the class**

Expected: PASS.

- [ ] **Step 5: Write the failing tests for the wiring**

In `AppShellTest`. These drive the real sheet, so they write a task to the app's database; that is what every `connectedDebugAndroidTest` run already discards.

```kotlin
@Test fun savingATaskSendsAPillToItsTab()
/** Review Focus 2. */
@Test fun aSecondSaveReplacesTheFirstPill()        // never two at once
/** Review Focus 3. */
@Test fun tappingAddCancelsTheFlightAndOpensFocused()
/** Review Focus 4. */
@Test fun aPillFliesFromAScreenThatIsNotATab()     // from a task detail; the selected tab does not change
```

- [ ] **Step 6: Run them and watch them fail**

- [ ] **Step 7: Wire it into `AppShell`**

The shell remembers the tab bounds and the add button's bounds, holds at most one pending flight, starts one in `onSaved` using `arrivalTab(saved, LocalDate.now())`, clears it when the add button is tapped, and pulses the destination tab when the flight finishes. The overlay is a sibling of the `Scaffold` inside a wrapping `Box`, so the pill can cross from the content area into the bar.

- [ ] **Step 8: Run the whole device suite**

Run: `gradlew.bat connectedDebugAndroidTest`
Expected: PASS, all 205 earlier tests included.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui app/src/androidTest/java/cz/olda/tasks/ui
git commit -m "feat: a saved task flies to the tab that holds it"
```

---

### Task 5: The destination tab pulses

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt` — `TabItem`

**Interfaces:**
- Consumes: the `pulsing: Destination?` the shell sets when a flight finishes (Task 4).
- Produces: no signature change outside `AppShell.kt`.

The pulsed tab's icon and label scale up briefly and settle on `AppMotion.fastSpatial()`. **This task ships no test, deliberately:** a scale applied for a few hundred milliseconds and then released leaves nothing a semantics assertion can see once it has settled, and the fixtures here cannot hold the clock before the flight that triggers it has finished. Say so in the code rather than writing a test that cannot fail — this project has already deleted two of those.

- [ ] **Step 1: Add the pulse to `TabItem`**

- [ ] **Step 2: Run the shell class**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.AppShellTest`
Expected: PASS — the pulse must not disturb `assertIsSelected` or the pill's measurements.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/AppShell.kt
git commit -m "feat: the tab a task landed on pulses once"
```

---

### Task 6: Olda feels it on his phone

Not code. Back up `tasks.db`, `gradlew.bat installRelease`, restore, then he says which numbers are wrong. Three are taste, not correctness: **the arc height** (`FlightArc`, 160.dp — whether the pill clearly leaves the bar), how far tab content travels, and whether the pulse reads at all.
