# Phase 1 · Core app, no sync — Design

Nov 23, 2026 · for Olda's task app
Spec this serves: `docs/design.md` · Phase 0 shipped in commits 17f6e80..4496523

## Intent and why

Phase 1 is the first phase where the app does real work: tasks go in, get organised,
get done, and can be found again. Everything lives on the phone; nothing talks to
Google yet.

It matters because nothing later in the roadmap can be judged until this exists. The
quick-add parser (Phase 2) needs tasks and lists to put things into. Sync (Phase 4)
needs a database to sync. And Olda can't tell whether the app he asked for is the app
he wants until he has used it for a week.

**Done when:** Olda uses the app daily, beside TickTick, for a week.

## Goals

- **Capture is fast.** From any screen, the add button opens quick-add; a task gets a
  date, a list and a priority without leaving the sheet.
- **See what's due.** Today, Upcoming and Inbox each answer one question without
  further tapping.
- **Organise.** Groups hold lists, lists hold headings, headings hold tasks, tasks
  hold one level of subtasks.
- **Edit, complete, delete** any task.
- **Find.** Search across every task by title and notes.
- **Lists have colour:** the 12 presets from *Look*, or any hex code.

## Non-goals

These are named because they are plausible here and deliberately out:

- Google sync, Google sign-in, any network access. No `INTERNET` permission.
- The **Day view** and the List/Day switch (Olda's call on 23 Nov: later, once tasks
  carry real durations).
- Natural-language quick-add — the parser is Phase 2. Phase 1 sets date, list and
  priority through chips.
- Reminders and recurring tasks (Phase 3).
- Focus timer (Phase 5).
- Tag UI. Tags exist in the database but nothing in Phase 1 shows or edits them.
- Drag-to-reorder.
- Duration UI.
- Importing from TickTick (Phase 6).

## Anti-goals

It fails even if it technically works when:

- Capturing a task takes longer than in TickTick. This is the reason quick-add gets
  chips rather than a title-only field.
- The codebase grows past what Olda and an AI assistant can follow together.
- The schema has to be migrated in Phase 2 or Phase 4 for something `docs/design.md`
  already knows about today.

## Constraints

From `CLAUDE.md` and `docs/design.md`:

- Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, one `app` module.
- Package `cz.olda.tasks`, minimum SDK 29.
- Code identifiers in English; every user-facing string in `res/values/strings.xml`.
- No dynamic colour. No `INTERNET` permission before Phase 4.
- Test first: write the failing test, run it, implement, run again.
- A task is done when `gradlew.bat testDebugUnitTest` and, with the phone connected,
  `gradlew.bat connectedDebugAndroidTest` both pass. Commit and push per task.
- Ask Olda before adding any library beyond the ones approved below.

What Phase 0 already provides and this phase builds on:

- `ui/AppShell.kt` — Scaffold, the floating bottom bar, the four destinations, and the
  add button that opens quick-add. The selected tab lives in `rememberSaveable`.
- `ui/theme/` — `AppTheme`, the light and dark colour schemes, Manrope, `ListPalette`
  (12 colours), `contrastRatio`.
- `ui/screens/PlaceholderScreen.kt` — replaced screen by screen in this phase.
- `ui/AppIcons.kt` — hand-drawn icons; new ones get added here, not pulled from a
  library.

One constraint that reaches forward: Google Tasks (Phase 4) syncs only title, notes,
due date, done and one level of subtasks, and every task must belong to a Google task
list. The schema below is shaped so Phase 4 needs no migration.

## Approaches

| Approach | What you get | Trade-off | Verdict |
| --- | --- | --- | --- |
| **A. Compose + Room + ViewModels, dependencies wired by hand** | A small `AppContainer` on the Application holds the database and repositories; a simple factory hands ViewModels their dependencies. About thirty readable lines of wiring and no extra dependency. | Wiring is written by hand, and grows if the app ever gets many modules. | **Picked** |
| B. The same, with Hilt for dependency injection | The standard Android answer; the wiring is generated. | A library, annotations, slower builds, and a layer that is hard to debug because nobody wrote it. At ten screens and one database it saves thirty lines. | Rejected for now; it can be adopted later if modules multiply |
| C. No ViewModels — composables read Room directly | Fewest layers. | State does not survive rotation, which Phase 0 deliberately tested for, and screens become hard to test without a device. | Rejected |

Olda picked A on 23 November. The reason that decided it is the anti-goal: Hilt is
machinery that works until it doesn't, and then the error is in generated code he never
wrote. For a first Android app, thirty lines he can read is the better trade.

### Libraries this phase adds

Approved by Olda on 23 November. All are AndroidX or Kotlin libraries from the same
families already in the build:

| Library | Why |
| --- | --- |
| `androidx.room:room-runtime`, `room-ktx`, `room-compiler` (+ the KSP Gradle plugin) | the on-device database |
| `androidx.navigation:navigation-compose` | moving between screens |
| `androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` | ViewModels, and reading database flows in step with the lifecycle |
| `androidx.room:room-testing`, `kotlinx-coroutines-test` | testing the database and suspending code |

Anything beyond this list is a question for Olda, per `CLAUDE.md`.

## The design

### Data

One Room database, version 1. Tables, with the columns Phase 1 writes and the ones it
only reserves:

- **`groups`** — id, name, order.
- **`lists`** — id, name, colour (hex), icon, group id, order, `is_inbox`,
  *reserved:* google list id.
- **`headings`** — id, list id, name, order.
- **`tags`** — id, name, colour (hex). *No UI in Phase 1.*
- **`task_tags`** — task id, tag id. *No UI in Phase 1.*
- **`tasks`** — id, title, notes, title/notes normalised, list id, heading id, parent
  task id, due date, due time, priority (1–4), done, completed at, order, `deleted_at`,
  *reserved:* duration, goal id, google id, last change time.

**Dates are not timestamps.** Due date is stored as a local epoch day and due time as
minutes since midnight, both nullable and independent. A task due "tomorrow" is due
tomorrow wherever Olda is; storing an instant would move it across midnight and over
daylight saving. It also matches what Phase 4 pushes to Google: a date with no time.

**Normalised columns.** `title_normalised` and `notes_normalised` hold the lowercased,
diacritics-stripped text, written whenever the task is written. SQLite's `LIKE` does not
fold Czech diacritics, so searching "zitra" would otherwise miss "zítra" — and
`docs/design.md` asks for diacritics-free input to work.

Four columns exist from day one with no UI behind them, because adding them later means
a migration for something already known:

- **duration** — the Day view will size blocks by it.
- **goal id** — `docs/design.md` says explicitly that every task should carry an
  optional goal link from the first schema version, so Phase 7 needs no rework.
- **google id / last change time** — Phase 4 sync.
- **`deleted_at`** — a delete has to be pushed to Google in Phase 4, which needs a
  tombstone rather than a vanished row. Phase 1 soft-deletes and filters.

Colours are stored as hex strings, never as palette indexes, so a preset that changes
later does not rewrite anyone's lists.

**Inbox** is a real row in `lists` with `is_inbox` set, created on first run. It cannot
be deleted and does not appear in the Lists screen — it has its own tab. Making it a
real list rather than "tasks with no list" is what lets Phase 4 map it to a Google task
list like any other.

### Screens

Each replaces a `PlaceholderScreen`, or is reached from one.

- **Today** — the List view from *Look*: a week strip with one dot per list that has
  tasks that day; then overdue tasks, then the day's tasks with a time in time order,
  then the day's tasks without a time ("Anytime"), then a "Tomorrow" row. No List/Day
  switch in the header until the Day view exists.
  - "Anytime" means *this day, no time set* — not tasks with no date at all. A task
    with no date appears only in its own list and in Inbox.
  - Tapping a day in the week strip shows that day, and the header names it. The
    "Tomorrow" row and the overdue section appear only when the day shown is today.
- **Upcoming** — tasks due after today, grouped by day.
- **Inbox** — the tasks in the Inbox list.
- **Lists** — groups, each with its lists, each list showing its colour and how many
  tasks are open.
- **List detail** — one list, its headings, and the tasks under each.
- **Task detail** — every field a task has in Phase 1, plus its subtasks.
- **Search** — one field, results across titles and notes.
- **Settings** — small; the colour section from *Look* lives here.

### Groups, lists and subtasks

Lists and groups can be created, renamed, reordered within their parent, recoloured and
deleted from the Lists screen. **Deleting a list moves its tasks to Inbox** rather than
deleting them, and says so; a list is easy to recreate, a week of captured tasks is not.
Deleting a group leaves its lists and moves them out of any group. Inbox can be neither
deleted nor moved into a group.

A subtask belongs to its parent's list and heading and moves with it. It carries its own
title, done state and order, and may have its own date and priority. **Completing a
parent completes its open subtasks**; reopening the parent does not reopen them. A
subtask cannot itself have subtasks — Google Tasks syncs one level and so does the app.

A task may sit directly in a list with no heading; those tasks come before the first
heading.

### Quick-add

The sheet Phase 0 built, with a row of chips under the field:

```
┌─────────────────────────────────┐
│ Zavolat na úřad                 │
│                                 │
│  [📅 Dnes]  [# Inbox]  [⚑ p3]   │
└─────────────────────────────────┘
```

Each chip opens a small picker and shows the current choice. Defaults: no date, the
Inbox list, priority 4. Phase 2's parser fills these same chips from the sentence, and
they stay as the place to see and correct what it understood.

### Colour picker

As `docs/design.md` → *Colours* specifies: the 12 preset swatches, plus Custom with a
hex field that accepts `#RRGGBB` with or without the `#` and refuses anything else. The
picker previews the colour on a light and a dark card, and warns — without blocking —
when text in that colour would fall below 4.5:1, using `contrastRatio` from Phase 0. A
new list takes the next preset in order.

### How the pieces fit

Room DAOs return `Flow`s. A repository per area (tasks, lists, groups, tags) wraps the
DAOs. A ViewModel per screen turns a repository flow into screen state, which the
composable reads with `collectAsStateWithLifecycle`. `AppContainer`, held by the
Application, builds the database and the repositories once; a ViewModel factory hands
them out. Composables take plain state and callbacks, so they can be tested without a
database.

### Decisions made while writing this (Claude's calls)

Each is a call a builder could have made differently; they are collected here so Olda
can overrule any of them in one pass:

1. **Subtasks are in**, one level. They are part of the structure in `docs/design.md`
   and they touch the schema, so leaving them out means a migration later.
2. **Tags get their tables but no UI.** Phase 2 parses `@telefon`, so the tables have
   to exist by then; building the tag screens now would be work Phase 1 cannot show off.
3. **No drag-to-reorder.** The `order` column exists and new tasks append to the end.
   Reordering by hand is a small, separable piece of work for later.
4. **Completing a task** removes it from the list with an undo snackbar, rather than
   leaving it struck through.
5. **Deleting is a soft delete** (see `deleted_at` above).
6. **Navigation Compose** carries the navigation graph.
7. **Search is a plain `LIKE` query** over the normalised columns, so it ignores case
   and diacritics. Full-text search is worth it at thousands of tasks, not at hundreds.
8. **Deleting a list moves its tasks to Inbox.** The alternative loses work on a
   mis-tap, and nothing else in Phase 1 can get it back.
9. **Completing a parent completes its subtasks**, and reopening it does not reopen
   them.
10. **The week strip is tappable** and changes which day Today shows. A strip you
    cannot tap is decoration.
11. **Dates are stored as a local epoch day plus optional minutes since midnight**,
    not as an instant.
12. **"Anytime" means today with no time**, not undated.

## What is left to the builder

- The exact DAO methods and query shapes, and where each screen's state is assembled.
- The navigation graph's routes and argument types.
- Whether date and time use Material 3's pickers or something smaller and faster.
- How the week strip computes its per-list dots without a query per day.
- The database file name, and how the first run seeds the Inbox list.
- Where the undo snackbar's window lives and how long it lasts.
