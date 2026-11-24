# Phase 1 Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the five moments Olda named — tab pill, screen transitions, completing a task, quick-add, scrolling — Material 3 Expressive motion, without touching `TaskRow`'s measuring logic or the schema.

**Architecture:** One motion file holds the six Material expressive spring tokens, copied because material3 1.4.0 keeps them `internal`, plus a flag for the phone's "Remove animations" setting. Each moment is then animated where it lives. Completion is the only row disappearance that animates, so it is driven by an explicit signal from `rememberCompletionUndo` through a tracker provided by `AppShell`, not by diffing lists.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 1.4.0, `androidx.compose.animation` 1.10.4. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-24-phase-1-motion-design.md`

## Global Constraints

- No new libraries and no dependency version changes (CLAUDE.md: ask Olda first).
- No `@Entity` changes of any kind: schema stays at version 1 with no destructive-migration fallback, and Olda's real tasks are in the database (`docs/phase-1-handover.md`).
- User-facing text only in `res/values/strings.xml`; code identifiers in English.
- `data/` stays free of `android.*` imports and `Context` — guarded by `DataLayerIsPlatformFreeTest`.
- Spring values, exactly: default spatial 0.8/380 · fast spatial 0.6/800 · slow spatial 0.8/200 · default effects 1.0/1600 · fast effects 1.0/3800 · slow effects 1.0/800.
- Every animation is interruptible; no gesture waits for one.
- `TaskRow`'s width-measuring logic and its large-font tests are not to be modified.
- Device tests run only on the phone: `ANDROID_SERIAL="adb-FQAUKJPJVOKZVW8H-2NpXZJ._adb-tls-connect._tcp"`, phone awake and unlocked.

## Review Focus

1. **"Remove animations" is on.** Every moment must still work and land in its final state, not just the foundation. Tests in Tasks 2, 3, 5 and 6.
2. **Completing the last task of a section, or of the whole screen.** The header must not vanish under a still-folding row and the empty-state text must not appear over it. Test in Task 5.
3. **Undo tapped while the row is still folding.** The row must come back, not duplicate and not leave a ghost. Test in Task 5.
4. **A row leaving the list for any reason other than completion** — a search keystroke re-querying, the week strip swapping the day, a task re-filed on its detail screen. These must disappear at once, with no fold. Test in Task 5.
5. **A long Czech title in a row that is folding, at large font.** The fold animates height; the title must not reflow or break mid-word on the way out. Test in Task 5.

---

### Task 1: Motion foundation

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/theme/Motion.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/theme/Theme.kt:49-62`
- Test: `app/src/test/java/cz/olda/tasks/ui/theme/MotionTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `object AppMotion` with six generic functions, because these specs animate `Dp`,
    `Float` and `IntOffset` alike and a `SpringSpec<Float>` would only serve one of them:
    `fun <T> spatial(): SpringSpec<T>`, and the same shape for `fastSpatial`,
    `slowSpatial`, `effects`, `fastEffects`, `slowEffects`.
  - `fun animationsEnabled(animatorScale: Float): Boolean` — pure, so it can be unit-tested.
  - `val LocalAnimationsEnabled: ProvidableCompositionLocal<Boolean>`, default `true`.

- [ ] **Step 1: Write the failing test**

```kotlin
class MotionTest {
    @Test fun springsCarryTheMaterialExpressiveValues() {
        assertEquals(0.8f, AppMotion.spatial<Float>().dampingRatio, 0f)
        assertEquals(380f, AppMotion.spatial<Float>().stiffness, 0f)
        assertEquals(0.6f, AppMotion.fastSpatial<Float>().dampingRatio, 0f)
        assertEquals(800f, AppMotion.fastSpatial<Float>().stiffness, 0f)
        assertEquals(0.8f, AppMotion.slowSpatial<Float>().dampingRatio, 0f)
        assertEquals(200f, AppMotion.slowSpatial<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.effects<Float>().dampingRatio, 0f)
        assertEquals(1600f, AppMotion.effects<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.fastEffects<Float>().dampingRatio, 0f)
        assertEquals(3800f, AppMotion.fastEffects<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.slowEffects<Float>().dampingRatio, 0f)
        assertEquals(800f, AppMotion.slowEffects<Float>().stiffness, 0f)
    }

    @Test fun animationsAreOffOnlyWhenTheScaleIsZero() {
        assertFalse(animationsEnabled(0f))
        assertTrue(animationsEnabled(1f))
        assertTrue(animationsEnabled(0.5f))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `gradlew.bat testDebugUnitTest --tests "cz.olda.tasks.ui.theme.MotionTest"`
Expected: FAIL, unresolved reference `AppMotion`.

- [ ] **Step 3: Write `Motion.kt`**

A KDoc saying these are Material's published expressive motion tokens, copied because `MotionScheme.expressive()` and `MaterialTheme.motionScheme` are `internal` in material3 1.4.0, with the token names so a later reader can diff them against the library.

- [ ] **Step 4: Provide `LocalAnimationsEnabled` from `AppTheme`**

Read `Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)` once per composition of `AppTheme`, pass it through `animationsEnabled`, and add the result to the existing `CompositionLocalProvider`. No `ContentObserver`: the setting is rare and a restart picks it up. `AppTheme`'s signature does not change.

- [ ] **Step 5: Run the test and the full unit suite**

Run: `gradlew.bat testDebugUnitTest`
Expected: PASS, nothing else broken.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/theme/Motion.kt app/src/main/java/cz/olda/tasks/ui/theme/Theme.kt app/src/test/java/cz/olda/tasks/ui/theme/MotionTest.kt
git commit -m "feat: Material expressive motion tokens and an animations-off flag"
```

---

### Task 2: The bottom bar pill slides between tabs

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt:121-191`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt`

**Interfaces:**
- Consumes: `AppMotion.spatial`, `LocalAnimationsEnabled` from Task 1.
- Produces: the pill is tagged `"bottom-bar-pill"`; `TabItem` no longer draws its own background.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun theSelectedPillMovesToTheTappedTab() {
    rule.setContent { AppTheme { AppShell() } }
    val before = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot().left
    rule.onNode(hasText("Lists") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
    rule.waitForIdle()
    val after = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot().left
    assertTrue("pill did not move: $before -> $after", after > before)
}

/** Review Focus 1. */
@Test fun thePillStillLandsOnTheTabWithAnimationsOff() {
    rule.setContent {
        CompositionLocalProvider(LocalAnimationsEnabled provides false) { AppTheme { AppShell() } }
    }
    rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).performClick()
    rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).assertIsSelected()
    val pill = rule.onNodeWithTag("bottom-bar-pill").getUnclippedBoundsInRoot()
    val tab = rule.onNode(hasText("Inbox") and hasAnyAncestor(hasTestTag("bottom-bar"))).getUnclippedBoundsInRoot()
    assertTrue("pill is not over the Inbox tab", pill.left <= tab.left && pill.right >= tab.right)
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.AppShellTest`
Expected: FAIL, no node with tag `bottom-bar-pill`.

- [ ] **Step 3: Draw one pill behind the row of tabs**

One pill whose offset and width animate to the selected tab's measured bounds with `AppMotion.spatial`, snapping instead when `LocalAnimationsEnabled` is false. `TabItem` keeps its `selectable` semantics so the existing `assertIsSelected` tests stay true, but stops drawing its own background.

- [ ] **Step 4: Run the whole `AppShellTest` class**

Run: as in Step 2.
Expected: PASS, including the seven tests that were there before.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/AppShell.kt app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt
git commit -m "feat: the selected tab pill slides instead of teleporting"
```

---

### Task 3: The bar slims while scrolling down

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt:88-117`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt`

**Interfaces:**
- Consumes: `AppMotion.spatial`, `LocalAnimationsEnabled`.
- Produces: the bar's outer box keeps its full height permanently and is tagged `"bottom-bar-slot"`; `"bottom-bar"` stays on the visible pill-shaped surface, which is what shrinks.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun theBarSlimsOnTheWayDownAndReturnsOnTheWayUp() {
    rule.setContent { AppTheme { AppShell() } }
    val tall = rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height
    rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
    rule.waitForIdle()
    val slim = rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height
    assertTrue("bar did not slim: $tall -> $slim", slim < tall)
    rule.onNodeWithTag("screen-Today").performTouchInput { swipeDown() }
    rule.waitForIdle()
    assertEquals(tall, rule.onNodeWithTag("bottom-bar").getUnclippedBoundsInRoot().height)
}

/** The design reserves the height precisely so the scrolling content never relayouts. */
@Test fun theContentDoesNotMoveWhenTheBarSlims() {
    rule.setContent { AppTheme { AppShell() } }
    val slot = rule.onNodeWithTag("bottom-bar-slot").getUnclippedBoundsInRoot()
    rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
    rule.waitForIdle()
    assertEquals(slot, rule.onNodeWithTag("bottom-bar-slot").getUnclippedBoundsInRoot())
}

/** Capture must never get harder. */
@Test fun theAddButtonSurvivesTheSlimming() {
    rule.setContent { AppTheme { AppShell() } }
    rule.onNodeWithTag("screen-Today").performTouchInput { swipeUp() }
    rule.waitForIdle()
    rule.onNodeWithContentDescription("Add task").assertIsDisplayed().performClick()
    rule.onNodeWithTag("quick-add-field").assertIsFocused()
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: as Task 2 Step 2. Expected: FAIL, no `bottom-bar-slot`, height unchanged.

- [ ] **Step 3: Add a `NestedScrollConnection` in `AppShell`**

It drives one boolean — slimmed or not — from the scroll direction, animated with `AppMotion.spatial` and snapped when `LocalAnimationsEnabled` is false. The outer slot keeps the tall height so `Scaffold`'s `innerPadding` never changes. The add button's size is constant; only the tab labels and the surface height change.

- [ ] **Step 4: Run the whole `AppShellTest` class**

Expected: PASS, including `bottomBarStaysClearOfTheSystemBar`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/AppShell.kt app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt
git commit -m "feat: the bottom bar slims while you scroll down"
```

---

### Task 4: Screens enter and leave with a shared axis

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/nav/AppNavHost.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/nav/AppNavHostTest.kt`

**Interfaces:**
- Consumes: `AppMotion.spatial`, `AppMotion.effects`, `LocalAnimationsEnabled`.
- Produces: no signature change. `AppNavHost` keeps its current parameters.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun aDetailArrivesAndBackReturns() {
    // navigate to Routes.task(1), assert "screen-TaskDetail" displayed after waitForIdle,
    // press back, assert "screen-Today" displayed. Proves the transitions settle rather
    // than stranding the graph mid-animation.
}

/** Review Focus 1. */
@Test fun navigationIsInstantWithAnimationsOff() {
    // with LocalAnimationsEnabled false, the destination is displayed on the first frame
    // after navigate, without advancing the clock.
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.nav.AppNavHostTest`
Expected: the second test fails on the current default fade.

- [ ] **Step 3: Set the four transitions on the `NavHost`**

Forward: slide in from the trailing edge plus a fade, with `AppMotion.spatial` for the slide and `AppMotion.effects` for the fade. Pop: the reverse. The four top-level tab routes cross-fade with no travel, since they are siblings. When `LocalAnimationsEnabled` is false, all four are `EnterTransition.None` / `ExitTransition.None`. Back plays after the gesture; no seekable predictive-back work in this plan.

- [ ] **Step 4: Run the nav and shell test classes**

Expected: PASS, including `aDetailKeepsTheTabItWasOpenedFrom` in `AppShellTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/nav/AppNavHost.kt app/src/androidTest/java/cz/olda/tasks/ui/nav/AppNavHostTest.kt
git commit -m "feat: screens slide and fade instead of cutting"
```

---

### Task 5: A completed row fills, strikes through and folds away

The biggest task, and the one Olda will judge the phase by. It carries four of the five Review Focus items.

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/components/CompletionUndo.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/components/TaskListSection.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/components/TaskRow.kt` — **the signature only**, adding one parameter. The measuring logic is not touched.
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt` — provide the tracker
- Create: `app/src/main/java/cz/olda/tasks/ui/components/EmptyStateText.kt`
- Modify: the five screens that show empty text, to use it — `today/TodayScreen.kt`, `upcoming/UpcomingScreen.kt`, `inbox/InboxScreen.kt`, `lists/ListDetailScreen.kt`, `search/SearchScreen.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/components/TaskListSectionTest.kt`

**A correction to the design's sketch.** The design says the retention lives "above
`TaskListSection`" in a shared wrapper, reasoning that the component returns without
composing anything when its items are empty, so the header would vanish and the empty text
would pop over a folding row. The requirement is right; the sketch is more than it needs.
Retention lives *inside* `TaskListSection`, and its early return tests items **plus
retained rows**, which keeps the header for exactly as long as something is folding. Only
the screens' empty text sits above it, and it does not need the completed id — it only
needs to not arrive instantly, which a fade gives it. Hence `EmptyStateText`.

**Interfaces:**
- Consumes: `AppMotion.spatial`, `AppMotion.effects`, `LocalAnimationsEnabled`.
- Produces:
  - `class CompletionTracker { var justCompleted: Long? }` and `val LocalCompletionTracker: ProvidableCompositionLocal<CompletionTracker>`, provided by `AppShell` next to `LocalSnackbarHostState`. `rememberCompletionUndo` writes the id it completes and clears it on undo. Its returned `(Long) -> Unit` type does not change, so the five screens need no edit.
  - `TaskRow(..., showAsDone: Boolean = false)` — when true the checkbox renders filled and the title struck through, regardless of `item.task.done`.
  - `TaskListSection` keeps its current signature and gains the retention internally.
  - `EmptyStateText(text: String, modifier: Modifier = Modifier)` — the empty message, faded in with `AppMotion.slowEffects` so it cannot appear over a row that is still folding. It keeps whatever `testTag` the caller passes, so `today-empty`, `inbox-empty` and `search-no-results` stay findable.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun aCompletedRowIsStillThereWhileItFolds()           // present one frame after; absent once idle
@Test fun theFoldingRowShowsAsDone()                        // struck-through title while folding
/** Review Focus 4. */
@Test fun aRowThatLeavesWithoutBeingCompletedVanishesAtOnce()
/** Review Focus 2. */
@Test fun theHeaderStaysWhileTheLastRowFolds()
/** Review Focus 3. */
@Test fun undoDuringTheFoldBringsTheRowBackExactlyOnce()
/** Review Focus 5. */
@Test fun aLongCzechTitleDoesNotReflowWhileFolding()        // "zkontrolovat" at 2f, 360dp, one line throughout
/** Review Focus 1. */
@Test fun completionIsInstantWithAnimationsOff()
```

Drive the clock with `rule.mainClock.autoAdvance = false` and `advanceTimeBy`, and assert states around the animation, never intermediate pixels.

- [ ] **Step 2: Run them and watch every one fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.components.TaskListSectionTest`

- [ ] **Step 3: Add the tracker and report completions**

`CompletionTracker` in `CompletionUndo.kt`, provided by `AppShell`; `rememberCompletionUndo` sets `justCompleted` before calling `onComplete`, and clears it when undo runs.

- [ ] **Step 4: Add `showAsDone` to `TaskRow`**

A parameter read where the checkbox and the title style are decided. Nothing else in the file changes.

- [ ] **Step 5: Retain and fold in `TaskListSection`**

Keep rows that left `items` while their id was the tracker's `justCompleted`, render them with `showAsDone = true`, hold about 120 ms, then collapse the height and fade with `AppMotion.spatial` / `AppMotion.effects`, dropping the row once the transition is idle. The early `items.isEmpty()` return tests items plus retained rows, so the header outlives the last row for exactly as long as it is folding. Rows present at first composition appear without an entrance; rows arriving later animate in. With `LocalAnimationsEnabled` false there is no retention at all.

- [ ] **Step 6: Add `EmptyStateText` and use it in the five screens**

The message fades in with `AppMotion.slowEffects`, which is slower than the fold, so it can no longer appear over a row on its way out. Each call site passes the `testTag` it uses today.

- [ ] **Step 7: Run the new class, then every screen test class that lists tasks**

Run the full device suite: `gradlew.bat connectedDebugAndroidTest`
Expected: PASS, all 187 earlier tests included.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui app/src/androidTest/java/cz/olda/tasks/ui/components/TaskListSectionTest.kt
git commit -m "feat: a completed task fills, strikes through and folds away"
```

---

### Task 6: Quick-add rises out of the add button

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt:55-110`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt`

**Interfaces:**
- Consumes: `AppMotion.spatial`, `LocalAnimationsEnabled`.
- Produces: `QuickAddSheet`'s signature does not change. `ModalBottomSheet` stays.

- [ ] **Step 1: Write the failing tests**

```kotlin
/** Anti-goal 1: the animation must never gate the first keystroke. */
@Test fun theFieldTakesTextOnTheFirstFrame() {
    // open the sheet with mainClock.autoAdvance = false, type without advancing time,
    // assert the field contains the text
}
/** Review Focus 1. */
@Test fun theSheetContentIsFullSizeImmediatelyWithAnimationsOff()
```

- [ ] **Step 2: Run them and watch the first fail if focus is gated**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.quickadd.QuickAddSheetTest`

- [ ] **Step 3: Scale and fade the sheet's content from the add button**

`graphicsLayer` on the content with `transformOrigin` at the bottom-trailing corner, where the add button sits, animating scale and alpha with `AppMotion.spatial`. The existing `LaunchedEffect(Unit) { focusRequester.requestFocus() }` stays exactly where it is and is not moved into or after the animation.

- [ ] **Step 4: Run the class and `AppShellTest`**

Expected: PASS, including `backClosesQuickAdd` and `quickAddKeepsWhatYouTyped`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt
git commit -m "feat: quick-add rises out of the add button"
```

---

### Task 7: A detail screen fades its content in

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/task/TaskDetailScreen.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/lists/ListDetailScreen.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/task/TaskDetailScreenTest.kt`

**Interfaces:**
- Consumes: `AppMotion.fastEffects`, `LocalAnimationsEnabled`.
- Produces: no signature changes.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun theTaskFieldsArrivePresentAndEditable() {
    // with a task that loads after composition, assert the title field is displayed and
    // carries the title once idle - the fade must not leave it absent or non-interactive
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.ui.task.TaskDetailScreenTest`

- [ ] **Step 3: Wrap the loaded content in a fade**

`AnimatedVisibility` with a fade on `AppMotion.fastEffects` around the part that waits for data, in both screens. No fade when `LocalAnimationsEnabled` is false.

- [ ] **Step 4: Run both detail test classes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/cz/olda/tasks/ui/task/TaskDetailScreen.kt app/src/main/java/cz/olda/tasks/ui/lists/ListDetailScreen.kt app/src/androidTest/java/cz/olda/tasks/ui/task/TaskDetailScreenTest.kt
git commit -m "feat: detail screens fade their content in instead of popping it"
```

---

### Task 8: Olda feels it on his phone

Not code. `gradlew.bat installDebug`, then he uses it with his 31 real tasks and says which numbers are wrong — in particular the 120 ms hold before a row folds, the slimmed bar height, and whether the reserved strip under the bar annoys him. Those are the three the design flagged as taste, not correctness.
