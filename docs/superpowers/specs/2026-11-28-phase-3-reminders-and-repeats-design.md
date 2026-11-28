# Phase 3 · Reminders and recurring tasks — Design

Nov 28, 2026 · for Olda's task app
Spec this serves: `docs/design.md`, the Phase 3 roadmap row and the sections "Reminders" and
"Recurring tasks"
Phases 1 and 2 shipped and are in daily use with 31 real tasks on the phone; the database is at
version 2.

## Intent and why

The app knows when a task is due and says nothing. It stores that a task repeats and does nothing
with it. Phase 3 closes both.

A reminder is the one thing a task app does that a notebook cannot, and it is the reason a task
gets a time at all. Phase 2 made `ve 3 zavolat na urad` land at 15:00 — and then 15:00 arrives and
the phone is silent, so the task is only found by opening the app, which is exactly the habit a
reminder exists to replace.

Repeats are the other half, and Phase 2 built the near side of them on purpose: `každý den` is
recognised, folded into an RRULE fragment and stored in `TaskEntity.recurrence`, and
`recurrenceLabel` already says one in English. Nothing reads it. A repeating task today is an
ordinary task with a label on it: tick it and it is simply gone. Worse, there is still no way to
*set* a repeat on a task that exists — task detail can only clear one — so the field can be
emptied and never filled.

And once repeats work, they multiply. Six daily habits turn Today into a checklist of chores with
the day's real work buried under it, which is why `docs/design.md` names hiding them as a goal of
its own rather than a nicety.

**Done when:** a reminder fires on Olda's phone at the right minute and its Done and Snooze
buttons work; ticking a recurring task creates the next one on the right day; the "hide repeats"
chip works on Today and Upcoming and is still set after a restart; the JVM tests for the
next-occurrence logic and the ring-time logic pass; and `connectedDebugAndroidTest` is green with
his database backed up and restored around it.

## Goals

- **A reminder you can trust.** At the minute, not near it. It survives the app being closed, the
  process being killed and the phone being restarted.
- **Two buttons that mean it.** Done ticks the task off without opening the app. Snooze rings
  again in an hour and leaves the task's own due time alone.
- **A repeat that carries itself forward.** Ticking one creates the next occurrence on the right
  day, with everything about the task intact.
- **A repeat you can set, change and remove.** The picker Phase 2 left out, reachable from task
  detail and from quick-add.
- **Repeats out of the way, never lost.** One chip hides them; a folded row says how many went;
  overdue ones stay put.
- **Dates stay testable on the PC.** Which day the next occurrence falls on, and which minute a
  task should ring at, are pure Kotlin in `data/`, run by the JVM suite in seconds.

## Non-goals

Named because they are plausible here and deliberately out:

- **More than one reminder per task**, and "15 minutes before". One task, one ring. The data model
  does not forbid a second one later, but nothing in this phase has two.
- **A reminder on a task with no date.** There is no moment to ring at. Inbox items stay silent.
- **Re-firing a reminder missed while the phone was off.** See the builder's call below: it is what
  keeps the whole scheme stateless, and the task is still sitting on Today, overdue.
- **`UNTIL` and `COUNT`** — "repeat ten times", "every Monday until Christmas". The stored
  vocabulary has room for them (that was Phase 2's reason for choosing RRULE) and the picker can
  grow a row later.
- **The Day view's half of the hide switch.** `ViewSwitch.kt`'s Day segment is drawn and disabled
  because nothing writes a task's length yet. The switch lands on Today's List view and on
  Upcoming; the Day view inherits it when it arrives.
- **Recreating the subtasks of a recurring parent.** The next occurrence is one task.
- **Changing the morning hour, the snooze length or the notification sound in Settings.** Fixed
  values this phase; Settings gains exactly one row, and only when notifications are blocked.
- **A full-screen or alarm-clock style reminder**, custom vibration, or a reminder that repeats
  until acknowledged.
- **Google sync.** Still no `INTERNET` permission. Phase 4 takes the recurrence rule over
  unchanged, which is why it is stored in Google's own vocabulary.
- **New libraries.** Nothing is added. `AlarmManager`, `NotificationManagerCompat` and Room are
  already there.
- **A snooze that moves the task.** Snooze moves the ring. The task stays due when it was due and
  goes overdue normally.

## Anti-goals

It has failed, even if every test passes, if:

- A reminder arrives at the wrong minute, twice, or not at all.
- Ticking a repeating task loses it: no next occurrence, or one that lands in the past and stays
  there.
- A hidden repeat cannot be found again.
- A notification keeps coming back after it has been dealt with.
- The app's battery use becomes visible in Android's own list.
- His 31 real tasks come out of the migration changed.
- `data/` gains an `android.*` import and Phase 8 inherits the bill.

## Constraints

- **Platform-free core.** `DataLayerIsPlatformFreeTest` walks `data/` for `android.*` imports and
  the word `Context` and fails the build on either. `AlarmManager`, notifications, receivers and
  the permission prompt are Android-only and live outside `data/`, in their own small files.
  `java.time` and `androidx.room` are fine inside it.
- **A real database with real data in it.** `tasks` is at version 2 with Olda's 31 tasks and no
  destructive-migration fallback, and there must never be one. The one migration in this phase is
  additive and gets a test against the committed `schemas/2.json` before it is allowed near the
  phone.
- **minSdk 29, targetSdk 37**, which splits the exact-alarm permission in three (see Permissions).
- **`POST_NOTIFICATIONS` is a runtime permission** from API 33: declaring it is not enough, the app
  has to ask, and a refusal has to be survivable.
- Package `cz.olda.tasks`, one `app` module, Gradle Kotlin DSL. Code identifiers in English; every
  user-facing string in `res/values/strings.xml`. There is no `values-cs`; the interface is
  English.
- **Icons are drawn in `AppIcons.kt`**, not pulled from an icon library, to keep the dependency
  list short. A repeat mark means one more path there.
- **His phone was not reachable on 28 Nov** (`adb` reports the wireless device as not found).
  Wireless debugging needs reconnecting before the first device run of this phase.

## Permissions

Three new lines in the manifest. Olda approved all three on 28 Nov, and chose the
granted-on-install route for exact alarms.

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"
    android:maxSdkVersion="32" />
```

- **`USE_EXACT_ALARM`** (API 33+) is granted at install and cannot be revoked, so
  `canScheduleExactAlarms()` is always true and there is no Settings trip and no refusal path to
  build. Google restricts it to alarm clocks and calendars *for apps on the Play Store*; this app
  is installed straight from Android Studio and never goes there, which is why the restriction
  does not apply and why the alternative was not worth its moving parts.
- **`SCHEDULE_EXACT_ALARM` capped at API 32** covers Android 12 and 12L, where `USE_EXACT_ALARM`
  does not exist yet; it is granted automatically on those versions. Below API 31 an exact alarm
  needs no permission at all. All three together mean "exact alarms work on every version this app
  supports, without asking".
- **`POST_NOTIFICATIONS`** is asked for once, the first time the app is opened on API 33+. If it is
  refused, everything else still works and Settings grows one row saying reminders are off, with a
  button to the system page. It is never asked again by the app — Android itself stops showing the
  dialog after two refusals, so "asked once" has to be the app's own memory: a second key in the
  `settings` table (`notificationsAsked`), which is the call made after the design review. The
  Settings row appears whenever `areNotificationsEnabled()` is false *and* the question has been
  asked, so a fresh install does not show an explanation for something it has not asked about yet.
  `SettingsScreen` has no ViewModel today and gains a small one for that single flag.
- **`RECEIVE_BOOT_COMPLETED`** lets a receiver re-book the next alarm after a restart. Without it
  every pending reminder is lost until the app is next opened.

**Four things re-arm the alarm, not one** — the call made after the design review, which pointed
out that a booked alarm is an absolute instant and three ordinary events leave it wrong with
nothing to correct it. The same receiver answers all four, and none of them needs a permission
beyond the three above:

| Intent filter | Why |
|---|---|
| `BOOT_COMPLETED` | A restart cancels every pending alarm. |
| `TIMEZONE_CHANGED` | A flight: 15:00 was booked as an absolute instant and is now the wrong local hour. |
| `TIME_SET` | The clock was changed by hand or by the network. DST transitions arrive here too. |
| `MY_PACKAGE_REPLACED` | **Olda's own workflow.** Every `gradlew installDebug` replaces the package and cancels its alarms, so without this line reminders quietly stop every time a new build goes on the phone — which is several times an evening while this phase is being built. |

Still **no `INTERNET` permission** until Phase 4.

## Approaches

Three ways to make a notification happen at a given minute. There is one sensible answer here, and
Olda took it on 28 Nov after seeing why the others do not fit.

| | What it is | Trade-off | Verdict |
|---|---|---|---|
| **A. `AlarmManager`, one alarm at a time** | The app books one exact alarm for the next reminder due. Android wakes a receiver at that minute, which posts the notification and books the one after it. | Exact to the minute, nothing of the app runs in between, no battery cost, survives process death. Needs the permissions above and a receiver that can run with no app process alive. | **Picked** |
| **B. `WorkManager`** | The library `docs/design.md` already names for Phase 4's sync. | Cannot do this job: its shortest periodic interval is 15 minutes and it is deliberately inexact, so a 15:00 reminder can land at 15:12. It stays in Phase 4 where inexactness is free. | Rejected |
| **C. A foreground service with its own clock** | A service that stays alive and watches the time itself. | Exact, and immune to every Doze rule — at the price of a permanent "Tasks is running" notification in the status bar and real battery drain, to deliver three buzzes a day. | Rejected |

Two further choices inside A, both the builder's call rather than Olda's:

- **One alarm, not one per task.** Booking an alarm for every pending reminder means hundreds of
  registered alarms to keep in step with every edit. Booking only the earliest and re-booking after
  each firing keeps exactly one thing to get right, and re-booking the same `PendingIntent` is
  idempotent.
- **"Hide repeats" filters in the repository, not in SQL.** The folded row has to say how many were
  removed, and tapping it has to show them, so the hidden tasks are needed either way; filtering
  the lists the DAO already returns gives both for free, where SQL would need a second query and a
  variant of every day query. At 31 tasks the cost is nothing.

## How it works

### The shape of it

```
   tasks table ─────────────────────────────────┐
        │ (Room invalidates on every write)     │
        ▼                                       │
   observeNextReminder()      pure: ringAt() per task, earliest future one wins
        │  Flow<Ring?>                          │
        ▼                                       │
   ReminderSync  (Android-only, app process)    │
        │  book / cancel exactly one alarm      │
        ▼                                       │
   AlarmManager.setExactAndAllowWhileIdle       │
        │  fires at the minute, app or no app   │
        ▼                                       │
   ReminderReceiver ──► notification  ──► [Done]    ─► setDone(true) ──┐
        │                                  [Snooze]  ─► snooze(+1h) ───┤
        │                                  [tap]     ─► task detail    │
        └──► rearmNow()  ◄──────────────────────────────────────────────┘
                 ▲
        BootReceiver (after a restart)
```

Nothing holds state between rings. The alarm is a function of the tasks table, recomputed whenever
the table changes, and re-booking it twice is the same as booking it once. That property is what
makes the rest of this safe, and it is why one tempting feature is out (see "A missed ring is not
re-fired").

### When a task rings

| The task | Rings at |
|---|---|
| Has a date and a time (`dueMinute`) | that date at that minute |
| Has a date, no time | that date at **08:00** |
| Has been snoozed, and the snooze is still ahead | the snooze time, whatever the above says |
| Has no date | never |
| Is done, or soft-deleted | never |

08:00 is Olda's choice on 28 Nov, and it is the same hour his parser already reads `ráno` as. A
dated task with no time is the common shape of his real tasks, so without this row most of his
tasks would never ring at all.

The pure part, in `data/reminder/`:

```kotlin
/** What the planner needs to know about one task; a projection, not the entity. */
data class ReminderInput(
    val taskId: Long,
    val dueDate: Long?,      // local epoch day, as TaskEntity stores it
    val dueMinute: Int?,     // minutes since local midnight
    val snoozedUntil: Long?, // epoch millis, or null
)

/** One moment, and every task that should ring at it — several share 08:00 routinely. */
data class Ring(val atMillis: Long, val taskIds: List<Long>)

/** The instant this task should ring, or null if it should not ring at all. */
fun ringAt(task: ReminderInput, zone: ZoneId, morningMinute: Int = 8 * 60): Long?

/** The earliest ring strictly after [nowMillis], with every task sharing that instant; null when nothing is coming. */
fun nextRing(tasks: List<ReminderInput>, nowMillis: Long, zone: ZoneId, morningMinute: Int = 8 * 60): Ring?
```

**A ring is a moment with a list of tasks, not one task** — the call made after the design review,
and the one that was a bug rather than a gap. Six dated tasks with no time all ring at 08:00. With
one task per ring, the alarm fires, the receiver posts the first task and re-arms with a `now` a
millisecond later, and the other five — whose ring is no longer *after* now — are skipped and never
ring at all, which is the top anti-goal of this phase delivered by the design meant to prevent it.
So the planner groups by instant, the alarm carries that instant, the receiver posts every task in
it and then the group summary, and the re-arm's "strictly after now" is safe precisely because
everything at that instant has just been posted.

`ZoneId` and `java.time` are allowed in `data/`; nothing here reads a clock or a time zone of its
own, both are passed in, so a test can say "it is 16:30 in Prague" and get one answer. The
repository supplies them from the `Clock` that `AppContainer` already owns, which is the same clock
every view already uses to decide what "today" means.

`TaskRepository` gains:

```kotlin
/** The one ring to book, recomputed by Room whenever any task changes. */
fun observeNextReminder(): Flow<Ring?>

/** Moves this task's ring, not its due time. [untilMillis] of null clears a snooze. */
suspend fun snooze(id: Long, untilMillis: Long?)
```

built on one new DAO query — open, live, dated tasks, which at this size is a handful of rows:

```sql
SELECT id, dueDate, dueMinute, snoozedUntil FROM tasks
WHERE deletedAt IS NULL AND done = 0 AND dueDate IS NOT NULL
```

### A missed ring is not re-fired

**The builder's call, and the one real cost in this design.** A ring whose minute has already
passed is skipped, never fired late. So a reminder due at 15:00 while the phone is switched off
does not arrive when the phone comes back; the task is on Today, overdue, which is where the app
shows it anyway.

The reason is the diagram above. `observeNextReminder()` re-emits on *every* write to the tasks
table — completing something, editing a title, adding a task. If a past ring could still fire, each
of those writes would fire it again, so the app would need to remember "this one has already been
shown", which means another column, another migration, and a new way to be wrong (a notification
Olda dismissed coming back). Skipping the past keeps every re-computation idempotent: booking the
same alarm twice is the same as booking it once, and there is nothing to remember.

Firing a ring missed in the last few minutes — the restart case — is listed under "left out, cheap
later" with the column it needs.

### The notification

One notification per task, so Done and Snooze always mean one specific thing. Several in the same
morning are bundled by Android under a summary, which is why they are posted into a group.

```
┌─────────────────────────────────────────┐
│ Tasks · 🌊 Práce              15:00     │
│ Zavolat na úřad                         │
│                                         │
│   [ Done ]        [ Snooze ]            │
└─────────────────────────────────────────┘

  three untimed tasks at 08:00, collapsed by Android:
┌─────────────────────────────────────────┐
│ Tasks · 3 tasks today            08:00  │
│ Koupit zimní kabát, Napsat na ekono…    │
└─────────────────────────────────────────┘
```

- **Channel:** one, id `reminders`, name from `strings.xml`, importance high (it buzzes and shows
  on the lock screen), created on app start — creating a channel twice is a no-op, so no state is
  needed.
- **Content:** title is the task's title; the line under it is the list's name. Nothing else. The
  notification is not a second task row.
- **Id** is the task's own id as an `Int`. Room's ids start at 1, so **0** is free and is the
  group summary's id.
- **Tap** opens the app on that task's detail screen: `MainActivity` is started with the task id as
  an intent extra and navigates to it once the nav host exists. A tap also cancels the
  notification.
- **Done** ticks the task off through the same `TaskRepository.setDone` every screen uses — so a
  recurring task creates its next occurrence right there, from the notification, with no app open.
  There is no undo snackbar on this path, and no attempt to invent one.
- **Snooze** writes `snoozedUntil = now + 1 hour` and lets the Flow re-book the alarm. Tapping
  Snooze on the next ring adds another hour. Olda's choice on 28 Nov.
- Both buttons cancel the notification, and both go to the same receiver with the task id in the
  intent's data (`tasks://reminder/<id>/done`), which is what keeps their `PendingIntent`s from
  colliding. `FLAG_IMMUTABLE` is required from API 31.
- A receiver cannot wait for a coroutine, so Done and Snooze take the broadcast's
  `goAsync()` result and finish it when the write and the re-arm are done.

### Ticking a repeat creates the next one

The anchor is the task's own due date, and whole periods are stepped off it until the result is
**after today**. Olda's choice on 28 Nov: keep the schedule, skip what was missed.

```kotlin
// data/recurrence/
/**
 * The next date in [rule]'s series after [today], anchored on [dueDate].
 * Null when the rule cannot be read, so an unknown rule never creates a task on a guessed day.
 */
fun nextOccurrence(rule: String, dueDate: LocalDate?, today: LocalDate): LocalDate?
```

| Rule | Due | Ticked on | Next | Why |
|---|---|---|---|---|
| `FREQ=DAILY` | Mon 1 Dec | Wed 3 Dec | **Thu 4 Dec** | steps 2, 3, 4 — the first after today |
| `FREQ=DAILY` | Mon 1 Dec | Mon 1 Dec | **Tue 2 Dec** | on time: exactly one step |
| `FREQ=DAILY` | Fri 5 Dec | Wed 3 Dec | **Sat 6 Dec** | early: still one step off its own due date |
| `FREQ=DAILY;INTERVAL=3` | Mon 1 Dec | Fri 5 Dec | **Sun 7 Dec** | 4, 7 — the rhythm is kept, not restarted |
| `FREQ=WEEKLY;BYDAY=MO` | Mon 1 Dec | Wed 3 Dec | **Mon 8 Dec** | still a Monday |
| `FREQ=WEEKLY;BYDAY=MO` | Wed 3 Dec | Wed 3 Dec | **Mon 8 Dec** | the rule's weekday wins over the due date's |
| `FREQ=MONTHLY` | 31 Jan | 31 Jan | **28 Feb** | `java.time` clamps to the month's last day |
| `FREQ=MONTHLY;INTERVAL=3` | 1 Mar | 1 Mar | **1 Jun** | quarterly; new in this phase, see the picker |
| `FREQ=YEARLY` | 29 Feb 2028 | 29 Feb 2028 | **28 Feb 2029** | same clamping |
| `FREQ=YEARLY;INTERVAL=2` | 1 Jun 2026 | 1 Jun 2026 | **1 Jun 2028** | new in this phase |
| `FREQ=DAILY` | *no date* | any day | **tomorrow** | today is the anchor when there is none |
| `FREQ=HOURLY` (unreadable) | any | any | **null** | nothing is created; the tick is an ordinary tick |

- **A `BYDAY` rule is anchored on its own weekday**, not the due date's: the candidate dates are the
  `BYDAY` weekdays after the due date. A task due on a Wednesday with "every Monday" on it is
  reachable through the picker, and it must move to a Monday rather than repeat on Wednesdays.
- **The step count is capped** (a thousand periods, which is a century of months) so a rule anchored
  in 1970 cannot spin. Past the cap it returns null.
- Clamping (31 Jan → 28 Feb) is `java.time`'s own behaviour, taken as-is and tested, not corrected.
  A monthly task whose due date is the 31st will walk itself onto the 28th; `BYMONTHDAY` is the
  proper fix and is out of this phase's vocabulary.

**Completing and creating happen in one database transaction**, in `TaskDao`, so a crash between
them cannot lose the task:

```kotlin
@Transaction
open suspend fun completeAndCreateNext(id: Long, nextDueDate: Long, now: Long): Long
```

It marks the task done exactly as `setDone` does (subtasks included), inserts the next occurrence
with `INSERT INTO tasks (...) SELECT ...` off the row itself, and copies the task's `task_tags`
rows to the new id. Doing the copy in SQL means `TaskRepository` does not have to reach into
`TagRepository`, and the new row cannot drift out of step with the columns of the old one.

**What the next occurrence carries:** title, both normalised columns, notes, list, heading,
priority, `dueMinute`, the recurrence rule, the tags, the goal link — everything that is the task.
**What it does not:** `done`, `completedAt`, `deletedAt`, `snoozedUntil`, `googleId` (it is a new
task to Google), `parentTaskId`, and the subtasks of a recurring parent. `position` is the next free
one in its list, `updatedAt` is now.

**The completed copy keeps its rule.** Nothing is erased from a task that is now history, and
hiding only ever looks at open tasks, so a done row carrying `recurrence` costs nothing.

**Undo removes the occurrence it created.** `TaskRepository` remembers, in memory, the last
(completed id → created id) pair, and `undoCompletion` hard-deletes the created row when it matches
before reopening the original. A hard delete and not the usual soft one: from Olda's point of view
that row never existed, and a soft-deleted sibling would sit in the way of `nextPosition`. The
memory lives for the life of the process, which is longer than the snackbar that offers the undo,
and the notification's Done path offers no undo at all — so no screen changes and
`rememberCompletionUndo` is untouched.

### The repeat picker

Olda chose the tap list on 28 Nov, from three mockups.

```
┌──────────────────────────────┐
│  Repeat                   ✕  │
│  ──────────────────────────  │
│  Does not repeat             │
│  Every day                ✓  │
│  Every week on Tuesday       │
│  Every month                 │
│  Every year                  │
│  ──────────────────────────  │
│  Custom…                  ›  │
└──────────────────────────────┘

Custom…:   Every [ 2 − + ] [ days ⌄ ]        [ Cancel ]  [ Save ]
```

- **The rows are worded by `recurrenceLabel`'s own strings**, so the picker and the Repeat row on
  task detail can never disagree about what a rule means. "Every week on Tuesday" takes its weekday
  from the task's due date, or from today when it has none. (The mockup said "Every month on the
  2nd"; that is dropped, because the stored rule is `FREQ=MONTHLY` and detail would then say
  something else.)
- **What each row writes:** nothing / `FREQ=DAILY` / `FREQ=WEEKLY;BYDAY=TU` /
  `FREQ=MONTHLY` / `FREQ=YEARLY`. Custom writes `FREQ=<unit>;INTERVAL=n` for all four units,
  dropping the `INTERVAL` when n is 1.
- **Two new rules, `FREQ=MONTHLY;INTERVAL=n` and `FREQ=YEARLY;INTERVAL=n`** — the call made after
  the design review. The Custom sheet offers months and years, so it must be able to store "every 3
  months"; today `recurrenceLabel` reads that pair as the catch-all "Repeats" and Phase 2's parser
  never produces one, so picking quarterly would quietly store *monthly* and nothing would say so.
  The alternative — greying the number out for months and years — makes the sheet refuse something
  obvious. So `recurrenceLabel` gains two branches and two strings ("Every %d months", "Every %d
  years"), `nextOccurrence` gains the two rows above, and `RecurrenceLabelTest` gains its cases.
  No migration: the rule is a string, which is why Phase 2 chose RRULE. The quick-add parser is
  **not** taught the words for them; the picker is where they come from.
- **The current rule is ticked**, including when it is one only Custom can express, in which case
  Custom's own fields open on it.
- **Two entry points, one sheet.** The Repeat row on task detail opens it (its ✕ still clears the
  repeat, so removing one stays a single tap), and the quick-add `↺` chip opens it instead of
  clearing. That is what fixes the wart Phase 2 wrote down: today tapping the chip clears the
  repeat, the chip then disappears, and nothing on screen can bring it back until the field is
  emptied. With a picker behind the chip, "no repeat" becomes a row in a list that is always
  reachable, and `QuickAddViewModel`'s `NO_REPEAT` pin becomes an ordinary pinned value like the
  date and the time: what the picker last said wins over what is typed, until the field is cleared.
- **The quick-add repeat chip becomes permanent**, which it is not today — the call made after the
  design review. `QuickAddChips.kt` draws its row only `if (state.recurrenceText != null ||
  state.tagNames.isNotEmpty())`, so a chip that is only there once the parser found a repeat cannot
  be the way a repeat is *set*, and tapping it to clear one still leaves nothing to tap. It joins
  the date, time, list and priority chips as always drawn, reading "↺ Repeat" when there is no rule.
  Its label stays Olda's own typed words when the parser found the repeat, and switches to
  `recurrenceLabel`'s English when the rule came from the picker, because at that point there are no
  typed words to show. So `QuickAddState` keeps `recurrenceText` for the typed case and gains the
  picked rule beside it; the pin decides which one is in force.
- **Task detail has no Repeat row when a task does not repeat**, which is where setting one would
  naturally start. It gains one: the row is always there, reading "Does not repeat" when there is
  no rule.

### Hiding repeats

Olda chose the chip on 28 Nov.

```
┌──────────────────────────────────────────┐
│  Today   Tue 2 Dec          [List|Day]   │
│  M  T  W  T  F  S  S                     │
│  ↺ Repeats hidden                        │   ← the chip, dark when on
│  OVERDUE                                 │
│  ○ Mon  Napsat na ekonomku  ↺   Práce    │   ← overdue repeats always show
│  TODAY                                   │
│  ○ 9:00  Odevzdat dokumentaci    Škola   │
│  ○ 15:00 Zavolat na úřad       Osobní    │
│  ↺ 2 repeats hidden ────────────────  ›  │   ← tap to peek
└──────────────────────────────────────────┘
```

- **The chip** sits under the week strip on Today and under the header on Upcoming, reading "Hide
  repeats" when repeats are showing and "Repeats hidden" when they are not, dark when on.
- **One setting, not one per screen.** Flipping it on Today flips it on Upcoming too, and it
  survives a restart. The builder's call: wanting repeats out of the way is a mood, not a
  per-screen preference.
- **The folded row** ends the day's own tasks: "↺ 2 repeats hidden ›". Tapping it shows them
  underneath, for this visit only, without flipping the chip back — so a peek costs one tap and
  undoes itself when the screen goes away. Upcoming gets one such row per day that has hidden
  repeats.
- **Overdue repeats ignore the chip**, on Today and on Upcoming. From `docs/design.md`: something
  already missed is exactly what hiding must not swallow. Their ↺ is visible, so it is obvious why
  they are there.
- **Tomorrow's row keeps counting everything.** It is one line standing for a whole day, and
  filtering its number would make it disagree with the day Olda sees when he taps it. Tapping
  through to tomorrow shows the folded row, which explains the difference. The builder's call.
- **Every count on the screen keeps counting everything**, for the same reason — the call made
  after the design review, which found three places fed from the same day that the chip would
  otherwise quietly change:
  - `DayGlance`'s split bar and its "2 of 7 done" line take `sections.timed + sections.anytime`
    today, so they gain `+ sections.hiddenRepeats`. Without it the "7" shrinks while the
    `doneCount` behind the "2" does not, and the two halves of one sentence stop agreeing.
  - The week strip's dots come from `observeDots`, which the chip does not touch and must not: a
    day is coloured by what is due on it, not by what is on screen.
  - **"Nothing due today" must not appear while the folded row is there.** The empty state's
    condition gains `hiddenRepeats`, so a day whose only tasks are hidden repeats shows the folded
    row alone — which is the point of the folded row.

  One rule, said once: **the chip changes which rows are listed, and nothing that counts a day.**
- **The repeat mark** on a row is a small ↺ between the title and the list name, in the secondary
  text colour: one new path in `AppIcons.kt`, drawn only when `recurrence != null`, taking no width
  when it is absent. It goes on every screen's rows, because "this is a repeat" is true everywhere,
  and `TaskRow` is one component.

The hidden tasks are not dropped by the query, they are moved aside, so the folded row can count
them and the peek can show them with no second read:

```kotlin
data class DaySections(
    val overdue: List<TaskWithList>,      // never filtered
    val timed: List<TaskWithList>,
    val anytime: List<TaskWithList>,
    val tomorrow: List<TaskWithList>,     // never filtered; the row is a count
    val hiddenRepeats: List<TaskWithList> = emptyList(),
)

data class UpcomingDay(
    val date: LocalDate,
    val tasks: List<TaskWithList>,
    val hiddenRepeats: List<TaskWithList> = emptyList(),
)

fun observeDay(day: LocalDate, hideRepeats: Boolean): Flow<DaySections>
fun observeUpcoming(hideRepeats: Boolean): Flow<List<UpcomingDay>>
```

With `hideRepeats = false`, `hiddenRepeats` is empty and nothing else changes — which is also what
every existing test sees, since the parameter defaults to false.

The flag reaches the repository from the screen, not through it: `TodayViewModel` already
`flatMapLatest`es over the day it shows, and the setting joins that; `UpcomingViewModel` gains the
same. `TaskRepository` never learns that a settings store exists.

### What changes in the database

Version 2 → 3, one migration, two statements, both additive:

```sql
ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER DEFAULT NULL;
CREATE TABLE IF NOT EXISTS settings (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`));
```

- **`snoozedUntil`** (epoch millis, nullable) on `tasks`: the column Olda approved on 28 Nov. It is
  the only state a snooze has, and the reason a snooze survives a restart. It joins the columns
  `updateEditableAndFollow` writes **only** if a reason appears; this phase writes it through its
  own small query instead, so an ordinary edit of a task cannot silently cancel a snooze.
- **`settings`** is a two-column key-value table, read and written by a new
  `data/SettingsRepository` (`observeBoolean(key, default)`, `setBoolean(key, value)`), with two
  keys in use: `hideRepeats` and `notificationsAsked`. The builder's call, over Android's
  `SharedPreferences` or Google's
  DataStore: a Room table is observable as a `Flow`, which drops straight into the `combine` the
  Today screen already does, it needs no `Context` so it stays inside platform-free `data/`, and
  Phase 8's Windows app inherits it instead of needing its own.
- `MIGRATION_2_3` goes in `data/Migrations.kt` beside `MIGRATION_1_2` and is registered in
  `AppContainer`, which is the only place that builds the database. The exported `schemas/3.json`
  is committed, and `MigrationTest` gains a case that opens version 2 from the committed schema,
  writes a task with a repeat, migrates, and asserts the task is intact with `snoozedUntil` null
  and `settings` empty. **The `CREATE TABLE` above must be copied verbatim from the exported
  `schemas/3.json`**, or Room's own validation fails at open time on a mismatch it will not explain
  kindly.

### Where the Android-only files sit

A new package `cz.olda.tasks.platform` — everything in it is what Phase 8 replaces for Windows,
and nothing outside it is:

| File | What it does |
|---|---|
| `platform/ReminderScheduler.kt` | Books or cancels the one exact alarm. The only file that touches `AlarmManager`. |
| `platform/ReminderSync.kt` | Collects `observeNextReminder()` for the life of the process and books what it emits; `rearmNow()` for the receivers, which have no process to collect in. |
| `platform/ReminderReceiver.kt` | The alarm fires here: posts every task in that ring and the group summary, then re-arms. |
| `platform/ReminderActionReceiver.kt` | Done and Snooze from the notification. |
| `platform/BootReceiver.kt` | Re-arms after a restart, a clock or time-zone change, and a reinstall. |
| `platform/Notifications.kt` | The channel, the notification, the group summary, the intents. |
| `platform/NotificationPermission.kt` | The one-off `POST_NOTIFICATIONS` request and a way to read whether it was granted. |

`TasksApplication` creates the channel and starts `ReminderSync` on a process-lifetime scope;
`AppContainer` gains `settingsRepository` and whatever the scheduler needs.

### Strings

New entries in `res/values/strings.xml`: the channel name and description, the notification's Done
and Snooze labels, the summary line ("%d tasks today"), the chip's two states, the folded row
("%d repeats hidden", a plural), "Does not repeat", "↺ Repeat" for the empty quick-add chip,
"Custom…", the Custom sheet's unit names (days / weeks / months / years), "Every %d months",
"Every %d years", the repeat mark's content description, and the Settings row shown when
notifications are blocked. The existing `repeat_*` strings are reused by the picker as they are.

## What is left to the builder

- How `nextOccurrence` is implemented (arithmetic or stepping), as long as the table above holds
  and is tested row by row.
- The exact layout of the picker sheet and the Custom sheet, within the Look section of
  `docs/design.md`; whether Custom is a second sheet or an expanding section of the first.
- The shape of `ReminderInput`'s DAO projection, and whether `observeNextReminder` maps in the
  repository or the DAO.
- The ↺ path in `AppIcons.kt`.
- Test tags, and how the device tests drive a notification (posting through the receiver directly
  rather than waiting for a real alarm).
- Where the `POST_NOTIFICATIONS` request is hung in the Compose tree.
- The precise wording of the new strings.

## The decisions Olda made (28 Nov)

1. **Timed tasks ring at their minute; a dated task with no time rings at 08:00.** Not every task,
   and not opt-in per task.
2. **The next occurrence keeps the schedule and skips what was missed** — a daily task due Monday,
   ticked Wednesday, comes back on Thursday, and a weekly Monday task stays on Mondays.
3. **Exact alarms are granted on install** (`USE_EXACT_ALARM`), not requested in Settings.
4. **Snooze means an hour**, repeatable, and never moves the task's own due time.
5. **The repeat picker is a short list to tap**, with Custom… behind it — not a sentence builder
   and not a text field read by the parser.
6. **The hide switch is a chip under the week strip**, labelled, not an icon in the header.
7. **The snooze time gets a column on `tasks`**, rather than its own table or being forgotten on
   restart.

## The calls made after the design review

A fresh Android engineer read this document on 28 Nov and reported six places where they would have
had to guess. All six are settled above; these are the answers, so Olda can check the reasoning
rather than hunt for it. None of them changes a decision he made.

1. **A ring is a moment with a list of tasks.** One task per ring would have posted the first of
   his 08:00 tasks and silently dropped the rest — the phase's own top anti-goal, built in by the
   design meant to prevent it. The only one of the six that was a bug rather than a gap.
2. **`FREQ=MONTHLY;INTERVAL=n` and `FREQ=YEARLY;INTERVAL=n` are added to the vocabulary**, because
   the Custom sheet offers months and years and otherwise "every 3 months" would quietly store
   *monthly*. Two branches in `recurrenceLabel`, two strings, no migration.
3. **The quick-add repeat chip becomes permanent**, reading "↺ Repeat" when there is no rule.
   A chip that only exists once the parser found a repeat cannot be how a repeat is set, which was
   half the point of building the picker.
4. **The chip changes which rows are listed and nothing that counts a day** — the glance line keeps
   its "of 7", the week dots stay as they are, and "Nothing due today" no longer appears while a
   folded row sits under it.
5. **Four things re-arm the alarm, not one.** A reinstall cancels pending alarms, and Olda
   reinstalls several times an evening, so `MY_PACKAGE_REPLACED` matters more here than the boot it
   was grouped with; time-zone and clock changes would otherwise leave a booked alarm an hour off.
6. **"Asked once" for notifications is the app's own memory**, a second key in the `settings` table,
   and the Settings row appears only once the question has actually been asked.

## Deliberately left out, cheap to add later

- **Firing a ring missed in the last few minutes**, for the restart case. It needs a `notifiedAt`
  column so a dismissed notification cannot come back; with that column it is a few lines in the
  planner.
- **`UNTIL` and `COUNT`** — "every Monday until Christmas", "ten times". One row in the picker, one
  branch in `nextOccurrence`, no migration (the rule is a string).
- **`BYMONTHDAY`**, which is what a monthly task due on the 31st actually wants.
- **Several reminders per task** and "15 minutes before": its own table, and a reminder list on task
  detail.
- **Recreating a recurring parent's subtasks** with the next occurrence — a weekly shopping list
  with its items.
- **The morning hour, the snooze length and the notification sound in Settings**, once the fixed
  values annoy him.
- **The Day view's chip**, when the Day view arrives.
- **A "repeats" filter that is not all-or-nothing** (hide dailies but not monthlies).
