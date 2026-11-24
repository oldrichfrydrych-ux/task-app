# Phase 1 motion — design

24 November 2026. Approach A, chosen by Olda.

## Intent, and why now

The app is functionally complete and feels dead. Everything it does, it does by cutting:
a tab is selected with no travel, a screen replaces another with no relationship between
them, a completed row vanishes mid-gesture, the quick-add sheet appears from nowhere.

Olda raised this the hour his real TickTick tasks landed in the app, which is the point:
with 31 real tasks in it, the stillness is what you notice. He asked for Material 3
Expressive, fluent transitions, and motion in what already exists, and then named the
moments himself — tab bar, opening a task, completing a task, quick-add — and added
movement while scrolling.

`docs/design.md`, section **Motion**, is the standing spec. This document covers the part
of it that Phase 1's existing screens can carry.

## Goals

- The five moments Olda named read as cause and effect rather than as cuts.
- The motion is Material 3 Expressive in fact, not in name: the real published spring
  values, not springs that merely feel springy.
- The phone's own "Remove animations" setting wins, as the spec requires.
- Nothing becomes slower to do. Every animation is interruptible; no gesture waits for one.

## Non-goals

- **Moving the lists to `LazyColumn`** (approach B). It would hand us row reordering and
  insertion animations for free and fix a latent performance problem, but it restructures
  five screens and their tests during the week the app is being used for real.
- **Container transform / shared elements** (approach C): a row growing into the detail
  screen. Worth judging once the basics are in.
- The Motion bullets whose features do not exist yet: the List ↔ Day morph (no Day view),
  drag-to-reschedule (no dragging), and the live quick-add highlighting (no parser until
  Phase 2).

## Anti-goals — this fails even if it technically works

- **Capturing a task gets slower than TickTick.** This is the phase's own anti-goal and
  quick-add is one of the five moments; an entrance animation that delays the keyboard or
  the first keystroke is worse than no animation.
- **The `TaskRow` layout tests break, or Czech long words start breaking mid-word again.**
  Two fix rounds went into that measuring logic.
- **Motion that ignores the accessibility setting.** Someone who turned animations off did
  so for a reason, and vestibular discomfort is not a preference to override.
- **Jank.** A dropped-frame animation reads as a worse app than no animation at all.

## Constraints

1. **material3 1.4.0 keeps the expressive motion API internal.** `MaterialExpressiveTheme`,
   `MotionScheme.expressive()` and `MaterialTheme.motionScheme` are all `internal` in the
   version on the classpath, so app code cannot read the scheme. The six spring values are
   published Material tokens and get copied into our own file. No dependency change, which
   also honours the CLAUDE.md rule about asking before adding libraries.

   | Spec | Damping | Stiffness | Used for |
   |---|---|---|---|
   | default spatial | 0.8 | 380 | things that move or resize |
   | fast spatial | 0.6 | 800 | small, frequent movement |
   | slow spatial | 0.8 | 200 | large travel |
   | default effects | 1.0 | 1600 | colour, alpha — no overshoot |
   | fast effects | 1.0 | 3800 | quick fades |
   | slow effects | 1.0 | 800 | slow fades |

2. **No new dependencies.** `compose.animation` 1.10.4 is already on the classpath.
3. **The lists are `Column` + `verticalScroll`, not `LazyColumn`,** so `animateItem()` is
   unavailable and any row entrance or exit is ours to write.
4. **Completing a task removes it from the data before an exit animation could run.** The
   repository write makes the Flow re-emit without that row, so whatever animates the
   removal has to outlive the data: the list component must keep rendering a row that its
   input no longer contains, until the animation finishes.
5. **No entity changes.** Olda's week has started and the schema is version 1 with no
   destructive-migration fallback (see `docs/phase-1-handover.md`).

## Approaches

| | What it is | Trade-off |
|---|---|---|
| **A — foundation plus hand-wired moments** *(chosen)* | One motion file; each of the five moments animated where it lives. `TaskRow`'s internals untouched — the wrapper animates, not the measuring logic. | Every moment is written by hand. No general row reordering. |
| B — A plus `LazyColumn` | Adds free insert/remove/reorder animation everywhere and fixes composing every row. | Restructures five screens and their tests mid-week. |
| C — A plus `SharedTransitionLayout` | The row grows into the detail screen; the add button becomes the sheet. The most expressive result. | Experimental API, threads a scope through every screen, and can fight the back-stack-derived tab logic in `AppShell`. |

Olda chose A. B and C stay open and are better judged after a week of use — in particular,
whether row reordering is something he ever actually sees.

## The design

### 1. `ui/theme/Motion.kt` — the foundation

An `AppMotion` object exposing the six springs above as `FiniteAnimationSpec`, named for
what they are for (`spatial`, `fastSpatial`, `slowSpatial`, `effects`, …), with a comment
saying they are copied Material tokens and why they are not read from material3.

Alongside it, `LocalAnimationsEnabled`: false when the phone's animator duration scale is
0, which is what Accessibility → Remove animations sets. Read through `Settings.Global`
once where the theme is set up, not observed for live changes — the setting is rare and a
restart picks it up. When it is false, every animation in this document collapses to an
instant change or a plain fade, and nothing else about the app differs.

### 2. The bottom bar pill slides between tabs

Today the selected pill is a background on whichever tab is selected, so it teleports. It
becomes one pill that animates its position and width to the selected tab, with the
default spatial spring.

### 3. The bar slims while scrolling down

A `NestedScrollConnection` in `AppShell` watches the scrolling content and drives one
value: the bar's height and the visibility of the tab labels. Scrolling down slims it to
icons; any upward scroll brings it back. The add button never shrinks and never leaves —
capture must not get harder.

**The content does not reflow.** The bar is `Scaffold`'s `bottomBar`, so its height feeds
`innerPadding` and animating that height would relayout the whole scrolling `Column` every
frame — 31 non-lazy rows, with a feedback path back into the nested-scroll deltas that
drive the animation. So the tall height stays reserved and the bar shrinks visually inside
it. It costs a strip of screen that the floating bar's padding largely occupies anyway,
and it buys immunity from the jank anti-goal. If the reserved strip turns out to annoy,
reflow can be revisited on its own.

### 4. Screens enter and leave with a shared axis

`AppNavHost` gets enter and exit transitions: forward navigation slides in from the
trailing edge with a fade, back reverses it, and switching between the four top-level tabs
is a fade without travel, since tabs are siblings rather than a hierarchy.

**Back plays after the gesture, it does not follow it.** `targetSdk` is 37 and predictive
back is on, so a seekable transition driven frame by frame by the gesture is possible, but
it is a different implementation from `popEnterTransition`/`popExitTransition` and belongs
with approach C's ambitions rather than A's. Whatever predictive behaviour
navigation-compose gives for free is kept.

**A detail screen that has no data yet fades its content in.** `TaskDetailScreen` and
`ListDetailScreen` render only their header until the task arrives, which today is a
flash and behind a slide would be an empty screen travelling in with fields popping on
arrival. Holding the outgoing screen until the data lands would make navigation feel
slower, which is anti-goal 1, so instead the fields fade in with the fast effects spring
when they arrive. Room answers in a frame or two; the fade turns a pop into an arrival.

### 5. A completed row folds away

`docs/design.md` names three things in this moment: the circle fills, the title strikes
through, and the row folds away. All three happen: the retained row is shown as done, so
the checkbox fills and the title strikes, and after a brief hold of about 120 ms it
collapses its height to nothing and fades. The hold is what makes it read as "done" rather
than as "gone"; it is the first number to tune once Olda has it in his hand.

`TaskRow` takes a parameter that overrides the displayed done state. Its measuring logic
is not touched — anti-goal 2 stands.

**Only a completion folds, not every disappearance.** Rows leave their list constantly for
reasons that must not animate: `SearchViewModel` re-emits a whole new result set on every
keystroke, tapping the week strip swaps the whole day, and re-filing or rescheduling a
task on its detail screen drops it out of Today or Inbox. Folding those would be wrong, and
search typing would fold rows away letter by letter. So the fold is driven by an explicit
signal, not by diffing: `rememberCompletionUndo` already is the single funnel every screen
completes through, so it also reports the id it just completed, and that id travels to the
list component. The five screens that list tasks pass it through.

**The retention lives above `TaskListSection`.** That component returns without composing
anything when its items are empty, and the screens show their empty-state text the instant
the list goes empty. If retention lived inside it, completing the last task of a section
would make the header vanish instantly and "Nothing due today" appear over a row that is
still folding. So a shared wrapper holds the folding row, the section header and the
empty-state decision until the animation ends.

**Entrance is for rows that arrive while the screen is watching, not for the first load.**
Every ViewModel starts empty and emits after composition, and tab switches tear the
composition down and rebuild it, so a naive rule would animate all 31 rows in on every tab
change — straight into the jank anti-goal. The first emission after composition renders
without animation; rows arriving after that get an entrance, so a task added by quick-add
arrives instead of blinking into place.

The undo snackbar is unchanged. Undo re-inserts the row through the same entrance.

### 6. Quick-add rises out of the add button

Correcting this document's first draft: `QuickAddSheet` already uses M3 `ModalBottomSheet`,
which rises from the bottom edge — it does not appear centred. In 1.4.0 that component's
own show and hide motion is hard-coded internally and `SheetState` exposes no spec, so the
only way to make the sheet itself grow out of the add button is to replace the component
with a hand-written surface and re-own the scrim, drag-to-dismiss, predictive back, the
`adjustResize` keyboard behaviour and the guarantees `QuickAddSheetTest` and `AppShellTest`
already assert.

**We keep `ModalBottomSheet`.** That full button-to-sheet morph is the container transform
of approach C, which Olda declined; re-owning a Material component for it during his trial
week is the largest risk in the phase and it points straight at anti-goal 1. Instead the
sheet's *content* scales and fades up with its transform origin at the add button, on top
of the sheet's own slide. It reads as rising out of the button at a fraction of the cost.

The text field keeps focus from the first frame and the keyboard is requested immediately.
The animation must never gate the first keystroke (anti-goal 1).

## What is left to the builder

- Whether the pill animates via an offset and width or by animating the selected tab's own
  background bounds; whichever keeps the existing `assertIsSelected` semantics intact.
- The exact slimmed height of the bar, and the scroll distance that triggers it.
- The shape of the shared wrapper that holds a folding row, and how the completed id gets
  from `rememberCompletionUndo` to it without every screen growing state of its own.
- How to assert motion in tests. Compose's test clock can advance frames, so the honest
  assertions are about the states around an animation (the row still present one frame
  after completion, absent once settled) rather than about intermediate pixels.
- Whether `LocalAnimationsEnabled` is read in `AppTheme` or provided by `MainActivity`.

## Calls made after the builder check

A reviewer read this document and came back with eight places a builder would have had to
guess. One was a factual error, now corrected in moment 6. The rest were settled here
rather than sent to Olda, because the conversation and the anti-goals already decided
them; each is marked above. The two worth arguing with while reading:

- **Keeping `ModalBottomSheet`** (moment 6) reads as following from his choice of A over C.
  If he actually wants the add button to become the sheet, that is approach C for that one
  moment and a much larger job.
- **Reserving the bar's tall height** (moment 3) trades a permanently occupied strip of
  screen for freedom from relayout jank while scrolling.
