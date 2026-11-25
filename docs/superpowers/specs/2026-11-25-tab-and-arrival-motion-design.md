# Tab travel and task arrival — design

25 November 2026. Approved approach: an overlay in the shell.

## Intent, and why now

Two moments still read as nothing happening.

**Switching tabs.** The pill slides, but the content only cross-fades. That was a call made
in the Phase 1 motion design — "tabs are siblings, not depth" — and Olda has reversed it.
He wants the content to travel.

**Adding a task.** This one is not really a missing animation. Quick-add gives a task no
date and files it in Inbox, so adding one from Today closes the sheet and changes nothing
on screen: the task landed somewhere Olda cannot see. There is nothing to animate, and
adding effects to the sheet would not fix it. What is missing is an answer to "where did
it go".

Offered three ways to answer it — a confirmation line, the task flying to the tab it
belongs to, or giving tasks added from Today a date — Olda chose the flight. It matches
what `docs/design.md` already asks for under Motion: "things grow from where you tapped
and go where they belong."

## Goals

- Tab content travels, and the direction means something: it follows the tabs' own order.
- After a save, it is obvious where the task went, without reading anything.
- Both are Material 3 Expressive in fact: the spring tokens already in `AppMotion`.
- The phone's "Remove animations" setting still wins.

## Non-goals

- A true shared-element transition from the text field into the row. `ModalBottomSheet`
  runs in its **own window** on Android — a `ComponentDialog` with its own `WindowManager`,
  verified in the material3 1.4.0 sources — and a shared element cannot cross windows. The
  flying pill drawn by the shell is the closest honest substitute.
- Changing where a task goes. Giving tasks added from Today a date is a parser-shaped
  decision and belongs with Phase 2.
- Any change to the quick-add sheet's own entrance, which is already done.

## Anti-goals — this fails even if it technically works

- **Capture gets slower.** The flight must start after the save and must never stand
  between Olda and the next task. Tapping + again cancels it.
- **Noise.** A flight to the tab he is already looking at, where the row arrives by itself,
  is one animation too many.
- **It lies.** A pill that lands on Inbox when the task went to a list is worse than no
  pill at all.
- **It breaks the 205 tests** or touches an entity. The schema is version 1 with no
  destructive-migration fallback and Olda's real tasks are in it.

## Constraints

1. No new dependencies; `AppMotion` already holds the six expressive springs.
2. `save()` currently returns `Boolean` and clears the draft, so it cannot say what it
   wrote. It has to report the saved task; nothing else knows both the title and where it
   went.
3. The flight crosses from the content area into the bottom bar, which are different parts
   of the `Scaffold`. The overlay has to sit above the whole `Scaffold`, in root
   coordinates.
4. `TabStrip` already measures each tab's bounds for the pill, but keeps them private. The
   overlay needs the same measurements, so they move up into `AppShell`.
5. Animations off means neither the flight nor the pulse exists at all.

## Approaches

| | What it is | Why |
|---|---|---|
| **Overlay in the shell** *(chosen)* | The shell draws a pill at the add button and moves it to the destination tab, then pulses that tab. Tab content slides on the tabs' own axis. | The only one that can cross from the sheet's world into the bar. |
| Shared element from the field to the row | The typed text itself becomes the row. | Impossible: the sheet is a separate window. Verified, not assumed. |
| A confirmation line and a pulse, no flight | "Added to Inbox" with a link. | Cheapest, and it answers the question — but it is text, not motion. Olda declined it. |

## The design

### 1. Tab content travels

`AppNavHost`'s between-tabs branch stops cross-fading. Direction comes from
`Destination.entries` order, which is also the order they appear in the bar, so the content
moves the way the eye expects. Non-tab navigation is unchanged. With animations off, both
are `None`, as now.

**Siblings travel differently from depth.** Opening a task already slides the new screen in
from the trailing edge while the one behind steps back a quarter — a push, and it means
"deeper". If tabs did the same, the app would stop distinguishing sideways from downwards,
which is most of what the Phase 1 scheme was for. So tabs get a shared axis instead: both
screens travel the **same short distance** — a fraction of the width, not a full throw —
and cross-fade through each other. It reads as sliding a sheet of paper sideways rather
than stacking a new one on top. The exact distance is a number for Olda to react to.

### 2. `save()` says what it saved

`QuickAddViewModel.save()` returns `SavedTask?` instead of `Boolean` — null when nothing
was written, so every existing caller's `if (saved)` becomes `if (saved != null)`.

```
data class SavedTask(val title: String, val dueDate: LocalDate?, val toInbox: Boolean)
```

Facts, not a destination: deciding where that lands is the shell's business, and keeping it
out of the ViewModel is what makes the decision testable on its own.

### 3. Where it belongs

A pure function, unit-tested without a device:

```
fun arrivalTab(saved: SavedTask, today: LocalDate): Destination
```

- a date on or before today → **Today** (an overdue task shows there, in Overdue)
- a date after today → **Upcoming**
- no date, in Inbox → **Inbox**
- no date, in some other list → **Lists**

### 4. The flight

`AppShell` wraps its `Scaffold` in a `Box` and draws, above everything, a small pill
carrying the task's title. It starts at the add button and travels to the destination tab's
measured bounds, shrinking as it goes, and disappears on arrival. The destination tab then
pulses once.

**It arcs well above the bar, and that is not decoration.** The add button and every tab
live in the same floating bar, a few tens of dp apart in a bar barely 56dp tall. A pill
going straight from one to the other would be a hop so small it reads as a flicker,
partly hidden behind the tabs it passes — which is exactly the "nothing happened" this
whole piece exists to fix. So the pill rises clear of the bar, travels across open screen,
and comes down onto its tab. The height of the arc is the number that decides whether the
motion is seen at all, and it is the first thing for Olda to react to.

It moves by animated **offset**, not by a `graphicsLayer` transform. Both would look the
same; an offset is layout, so a test can see where the pill is, and this project has
already written and deleted two tests that could not fail because a transform is invisible
to the semantics tree.

Rules that make it behave:

- **Every save flies.** The first draft suppressed the flight when the destination was the
  tab already selected, on the grounds that the row arrives there by itself. That rule is
  wrong often enough to drop: the add button is on every screen, so a task dated today
  added from a task detail would be silently suppressed with no row anywhere in sight, and
  a task dated for a day being previewed in the week strip would fly to Upcoming while its
  row appeared in front of Olda. Telling "is this really on screen" from the shell needs a
  new signal out of every list screen — a cross-screen seam in a codebase whose stated
  anti-goal is clutter. Flying every time costs one redundant animation in one uncommon
  case and removes the whole class.
- **One at a time.** A second save replaces the first; tapping + cancels what is in flight.
- **Never in the way.** It starts after the write and is purely decorative; the sheet has
  already closed and the field is free.
- **Animations off: it does not exist.** No pill, no pulse.

### 5. The pulse

The destination tab's icon and label scale up briefly and settle, on the fast spatial
spring. The pill behind the selected tab is not involved — the tab being pulsed is usually
not the selected one.

### 6. Which "today"

`arrivalTab` takes the date as a parameter, and the shell passes `LocalDate.now()`. The app
reads dates through the `Clock` in `AppContainer` everywhere else, and threading that into
`AppShell` would change its signature and every shell and navigation test that composes it
bare. It buys nothing here: the only interesting branch — on or before today against after
today — lives in the pure function, where unit tests cover it without a device or a clock.
The device test proves that a flight happens and moves, which needs no particular date.

## Calls made after the builder check

A reviewer read this and found four places a builder would have had to guess. All four are
settled above rather than sent to Olda, because each was a hole in my own reasoning rather
than a question about what he wants. Two are worth arguing with while reading:

- **The pill arcs above the bar** (§4). Without it the "flight" is a few tens of dp inside
  the bar and he would report the same "nothing happened" that started this.
- **Every save flies** (§4). Simpler, never silent, at the cost of one redundant animation
  when a task dated today is added while standing on Today.

The other two: tabs get a shared axis rather than reusing the push, so sideways still
looks different from deeper (§1); and the shell uses `LocalDate.now()` rather than growing
a clock parameter (§6).

## What is left to the builder

- Whether the tab slot measurements are hoisted into `AppShell` as state or passed back
  through a callback, and the same for the add button's position.
- The pill's exact size and whether the title is truncated or shown in full.
- The arc: a straight line is acceptable; a slight curve is nicer if it costs nothing.
- Whether the pulse is a scale, a brief tint, or both.
- How to assert the flight. Its offset is observable, so a test can hold the clock, read
  the pill's position a few frames in and again near the end, and check it has moved
  towards the destination tab's bounds. The pulse is a transform and is not assertable;
  that should be said rather than faked.
