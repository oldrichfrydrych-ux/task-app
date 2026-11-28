# Own task app — design & roadmap

Nov 23, 2026 · Oldřich Frydrych
Live version with diagrams: https://claude.ai/artifact/St9TAeWyo8ztnwEN1Tex6W

## Intent and why

A personal Android task app that is a pleasure to use, with natural-language quick-add in Czech and English, built on Google Tasks so tasks still show up in Google Calendar and Gmail.

- **Why not TickTick:** the design doesn't suit Olda, and it lacks the natural-language input he wants.
- **Why Google Tasks underneath:** its own app is barebones; its only value is living in the Google ecosystem and working with Calendar. Here it is the sync layer, not the app.
- **Inspiration:** Todoist for clean, fast plain-language quick-add; Memorigi for a distinctive look and structure (groups, lists, headings, tasks, tags).
- **Also an experiment:** a first Android app built with AI, so learning that workflow is part of the point.

## Goals, non-goals, anti-goals

**Goals**

- A look Olda enjoys: the warm "rail" direction he chose on 23 November (see Look).
- Capture by typing a sentence: "zítra ve 3 zavolat na úřad #práce" becomes a task with a date, a time and a list.
- Two-way sync with Google Tasks, so tasks appear in Google Calendar and Gmail.
- A focus timer and time tracker like TickTick's, tied to tasks.
- Recurring tasks can be hidden when they clutter the day.
- Learn how to build an Android app with AI.
- A Windows version later (Phase 8), built from the same Kotlin code rather than as a separate app.
- Share tasks and the Inbox with Do-day, Olda's companion calendar app (Phase 4b).
- Animations that feel good and explain what happened: quick, springy and never in the way (see Motion).

**Non-goals**

- iOS and web versions. Until the Windows version arrives, Google's own Tasks panel in Gmail and Calendar shows the tasks on a computer.
- Sharing, teams or collaboration.
- A Play Store release. The app goes straight from Android Studio onto Olda's phone.
- Matching TickTick feature for feature (habits, Eisenhower matrix). The focus timer is the exception: it is in, as Phase 5.

**Anti-goals** (it fails even if it technically works)

- Capturing a task is slower than in TickTick.
- Sync loses or duplicates tasks.
- The app slowly gets as cluttered as the ones it replaces.
- The code grows past what Olda and an AI assistant can follow.

## Constraints

- **What Google Tasks syncs:** title, notes (up to 8,192 characters), due date without a time, done or not, and one level of subtasks. It does not sync start dates, time of day, recurrence, priority, tags or reminders ([Tasks.org's sync table](https://www.tasks.org/docs/sync_google_tasks)).
- **Consequence:** the app keeps its own database on the phone as the source of truth. The time of day is also written as a line in the task's notes, so it is visible on the desktop. Calendar still shows such tasks as all-day.
- **Mixed Czech and English input:** ready-made date parsers mostly understand English only, so the app gets its own parser that handles both languages, even in one line.
- **Google sign-in:** the app needs its own Google Cloud project. While that project is in "Testing" status, [authorizations expire seven days after consent](https://support.google.com/cloud/answer/15549945), so switch it to "In production" (unverified) to avoid signing in every week.
- **Which Google account:** Olda's personal account (confirmed).
- **Time and skills:** evenings and weekends, first Android app. The AI writes most of the code; Olda steers, tests on his phone and reviews.

## Approaches

Olda picked A: build from scratch.

| Approach | What you get | Trade-off | Verdict |
| --- | --- | --- | --- |
| A. Native app from scratch (Kotlin, Jetpack Compose) | Full control of look and behaviour; a small codebase Olda and an AI can fully follow | Sync, reminders and widgets must be built | **Picked** |
| B. Fork Tasks.org and restyle it | Google Tasks sync, reminders and widgets work on day one | A Todoist or Memorigi look touches almost every screen of a large, mature codebase | Rejected |
| C. Cross-platform or web app | Could also run on the desktop | Not what was asked; the desktop is already covered by Google's Tasks panel | Dropped |

**Update 24 November:** Olda wants a Windows version eventually. That stays inside approach A: Kotlin Multiplatform with Compose Multiplatform builds a Windows app from the same Kotlin code, so no web app is needed (Phase 8).

## Design

One Kotlin app with three parts: a database on the phone holds everything, a parser turns Czech or English sentences into tasks, and a background job syncs the basics with Google Tasks. Only title, notes, date and done status travel to Google.

### Stack

- Kotlin and Jetpack Compose, with Material 3 as the base under a custom theme.
- Room (SQLite) for the database, WorkManager for background sync, scheduled notifications for reminders.
- Google sign-in through Google Identity Services; Google Tasks API over REST.
- **Ready for Windows (Phase 8):** the code stays in one `app` module until then, but the core (database entities, DAOs and repositories, text normalising, the quick-add parser, sync logic) uses no Android-only APIs: no `android.*` imports and no `Context`. Android-only work (building the database, WorkManager, notifications, Google sign-in) sits in its own small files. Phase 8 then moves the core into a shared Kotlin Multiplatform module instead of rewriting it.

### Structure

Memorigi-style: groups → lists → headings → tasks → one level of subtasks, with tags across lists. Each list maps to a Google task list; groups, headings, tags and priority stay in the app.

- **Task:** title, notes, list, heading, parent task, due date, due time, priority 1–4, tags, done, order, an optional duration, an optional goal or project, plus Google id and last-change time for sync.
- **List:** name, colour (a preset or any hex code), icon, group, Google list id.
- **Group, heading, tag:** name and order; tags also get a colour (a preset or any hex code).

### Czech and English quick-add

One line of text becomes a task. Symbols follow Todoist. Czech and English work side by side, even in the same line.

| You type | The app understands |
| --- | --- |
| dnes, zítra, pozítří / today, tomorrow | that date |
| v pondělí, příští pátek / on Monday, next Friday | the next such day |
| 24. 12., 24.12.2026 / Dec 24 | that date |
| ve 3, v 15:30, večer / at 3, at 3:30 pm, tonight | a time; "ve 3" or "at 3" means 15:00 (hours 1–7 read as afternoon) |
| za 3 dny, za hodinu / in 3 days, in an hour | relative date or time |
| každý den, každé pondělí / every day, every Monday | repeats |
| #práce | list |
| @telefon | tag |
| p1 to p4 | priority |

- Recognised parts highlight as you type and drop out of the title; tapping a highlight undoes it.
- Works without diacritics ("zitra").
- Backed by a test suite of 100+ real phrases in both languages from Olda's own tasks.

### Google Tasks sync

- Each sync pulls tasks changed since the last one and pushes local changes.
- Conflicts: the newer change to a task wins.
- Due time goes into the notes as a line such as "⏰ 15:30". Recurring tasks: when one is completed, the app creates the next one.
- Runs when the app opens, about a minute after an edit, periodically in the background, and on pull-to-refresh.
- Tags, priorities and headings exist only on the phone, so a backup to a file protects them.

### Reminders

A notification at the due time, with Done and Snooze buttons.

**Built on 28 November (Phase 3).** The decisions Olda made that day:

- A task with a time rings at that minute; a task dated with no time rings at **08:00**, the same
  hour the parser reads as `ráno`. A task with no date never rings.
- **Snooze means an hour**, and can be tapped again. It moves the ring and never the task: the task
  stays due when it was due and goes overdue normally.
- **Done** ticks the task off without opening the app, through the same path as ticking it on
  screen — so a repeating task creates its next occurrence from the notification.
- Exact alarms are granted at install (`USE_EXACT_ALARM`), so there is no Settings trip. The app
  asks once for permission to post notifications; if that is refused, Settings says so.
- **A reminder missed while the phone was off does not arrive late.** The task is on Today, overdue,
  which is where the app shows it anyway. Firing a just-missed one needs a column to remember what
  has already been shown, and that is written down as a later addition.
- **On Olda's phone a reminder arrives about a minute after its minute, and that is accepted.**
  Measured on 28 November: 74 seconds late, and 49 after the app was exempted from battery
  optimisation. ColorOS attaches a window to the alarm whatever the app asks for — the booking is
  made at the exact second with `setExactAndAllowWhileIdle` and `USE_EXACT_ALARM` granted, and
  `dumpsys alarm` still shows `window=+2m23s` while the app is warm and `+1h` once it has been
  closed, because Android defers allow-while-idle alarms past a handful an hour. The app no longer
  spends that allowance on every keystroke, which is what the alarm is re-booked on now.
  **The way to make it exact is `setAlarmClock()`**, which no policy may delay; its price is a
  permanent alarm-clock icon in the status bar, and Olda's call on 28 November was that a minute is
  not worth that. If the 08:00 nudge ever drifts far enough to annoy him, that is the lever.
- One alarm is booked at a time, re-armed after a restart, a clock or time-zone change, and a
  reinstall — the last because every `installDebug` cancels an app's alarms.

### Recurring tasks

Olda wants recurring tasks to be hideable; the details are Claude's proposal.

- A small repeat icon marks every recurring task.
- A "Hide recurring" switch on Today (both views) and Upcoming; the app remembers the choice.
- Hidden tasks collapse into one row, such as "3 recurring hidden", so nothing disappears silently. Overdue recurring tasks always stay visible.

**Built on 28 November (Phase 3).** As it turned out:

- **The next occurrence keeps the schedule and skips what was missed.** A daily task due Monday and
  ticked on Wednesday comes back on Thursday, not on the Tuesday it missed; a weekly Monday task
  stays on Mondays; "every 3 days" keeps its rhythm. Nothing it creates is ever already overdue.
- **A repeat picker**, reached from the Repeat row on task detail and from the ↺ chip in quick-add:
  Does not repeat, Every day, Every week on the task's own weekday, Every month, Every year, and
  Custom for every N days, weeks, months or years.
- The switch is a **labelled chip** rather than an icon, under the week strip on Today and under the
  header on Upcoming, and one choice serves both screens.
- **The chip changes which rows are listed and nothing that counts a day:** the glance line's "2 of
  7 done" still counts the hidden ones, the week dots are untouched, and "Nothing due today" never
  appears above a folded row.
- The Day view inherits the chip when it arrives; it is still drawn and disabled.

### Durations

A task can have an optional duration, kept only on the phone. The Day view sizes blocks by it and finds free time; a task without one shows as a 30-minute block.

### Focus timer and time tracking (Phase 5)

Olda wants TickTick-style focus time; the details are Claude's proposal.

- **Two modes:** Pomodoro (25 min focus, 5 min break, a 15 min break after four rounds; all adjustable) and a stopwatch that counts up.
- **Start from a task** (its row or detail) or on its own. The session is recorded against that task and its list.
- **Keeps running** when the app is closed or the screen is off, with pause and stop in a notification. A small running-timer chip shows on Today.
- **Records:** each session stores the task (optional), start, end, length and mode. Forgotten sessions can be added or corrected by hand.
- **Stats:** focus time today and this week, by list and by task. A task's detail shows its total tracked time.
- **Storage:** only on the phone, covered by the backup. Google Tasks gets nothing.
- **Navigation:** a Focus tab with a timer icon in the bottom bar.
- **Later ideas:** turn on Do Not Disturb during focus; show past sessions as thin bands in the Day view.

### Screens in the first version

Today (List and Day views), Upcoming, Inbox, Lists (by group), a list with its headings, task detail, the quick-add sheet, Search, Settings. Focus arrives in Phase 5.

### Look

Chosen on 23 November: the warm "rail" direction. Today shows the same tasks in two views, switched at the top right (mockups: https://claude.ai/artifact/7KbFvurP9WWc49FXhS2A2j, page "Chosen direction").

- **List view (default):** a compact rail with the start time on the left and the list name on the right. A thin bar under the week strip shows how the day splits across lists.
- **Day view:** a time grid from 08:00 to 20:00 with blocks sized by duration, free slots as dashed drop targets, and a day strip on top.
- **Shared frame:** header with the List/Day switch, a week strip with one dot per list that has tasks that day, overdue first, Anytime last, a "Tomorrow" row, and a floating bottom bar (a dark rounded pill) with a coral add button.
- **Type and shape:** Manrope, which covers all Czech letters. Cards have 16–22 px corners; buttons and bars are fully round; touch targets are at least 44 px.

| Colour role | Light | Dark |
| --- | --- | --- |
| Background | #FAF7F3 | #1A1714 |
| Surface (cards) | #FFFFFF | #24201C |
| Text | #1F1B17 | #F4EFE9 |
| Secondary text | #6B6157 | #BDB3A8 |
| Rail and grid lines | #E2DAD0 | #3A342E |
| Now line / its text | #E0603F / #C63F22 | #FF7A59 / #FF8E70 |
| Add button | #E2502A | #E2502A |
| Bottom bar | #1F1B17 | #2C2722 |

| List colour | Light | Dark |
| --- | --- | --- |
| Blue | #3D6BE0 | #7EA2FF |
| Orange | #E07B39 | #FF9F5A |
| Green | #23865A | #5ED29A |
| Purple | #7C4FE0 | #B794FF |
| Teal | #157D89 | #5CC8D2 |
| Red | #D43D3D | #FF7B7B |
| Pink | #C2418A | #F28BC4 |
| Amber | #B7791F | #F2C155 |
| Olive | #6E7F1E | #B7CC5A |
| Brown | #8A5A3C | #D1A07E |
| Slate | #5B6575 | #A9B3C2 |
| Cyan | #1E88B8 | #6EC6EE |

These are presets: any of them can be swapped for a custom colour (see Colours). Text written in a list's colour uses a darker shade in light mode so it keeps 4.5:1 contrast.

### Motion

Olda wants cool, intuitive animations in this app and in the companion calendar; the details are Claude's proposal.

- **Springs, not timers:** movement uses spring physics (Compose `spring()`), so it settles naturally and the next touch can interrupt it midway.
- **Fast:** most transitions finish in 200–350 ms. Nothing makes you wait before you can keep working.
- **Cause and effect:** things grow from where you tapped and go where they belong. The quick-add sheet rises out of the add button, a new task slides into its list or day, and a completed task's circle fills, its title strikes through and the row folds away.
- **Live quick-add:** recognised words light up with a small pop as you type; tapping a highlight to undo it fades it back into plain text.
- **Dragging:** a task dragged to another day or into a free slot in the Day view lifts with a shadow, the other items make room, and it snaps in with a light haptic tick.
- **List ↔ Day:** switching views morphs each task row into its time block instead of cutting between screens.
- **Bottom bar:** the selected-tab pill slides between tabs; the bar slims down while you scroll down and comes back when you scroll up.
- **Tab icons:** tapping a tab makes its icon do the thing the icon draws, and the part that moves is the part that means something: the clock's face sweeps a whole turn, the calendar's page slides out and the next one in while its frame stays put, something falls into the inbox and the tray gives under it, and a wave runs through the four tiles of the Lists icon, top-left to bottom-right. Each gesture winds up or recoils rather than sliding politely from A to B: the clock turns backwards before it spins, the tray rebounds past its own level before it settles. Every tap, including one on the tab you are already on. These are the one place the 200–350 ms rule above does not hold: they run close to a second, because nothing waits for them — the tab switches on the tap and the icon finishes afterwards — and at the speed the rest of the app moves they went by unread. Material 3 Expressive asks for spring physics and for things that answer a touch; the four gestures themselves are ours. A gesture that would suit any icon — a tilt, a bounce — suits none of them, which is why the three icons that need it are drawn from parts rather than as one shape.
- **The phone's setting wins:** with Android's "Remove animations" on, everything still works, with simple fades.

### Colours: presets plus any hex code

Every colour (lists, tags, priorities) starts as a preset and can be changed to any colour by hex code, as Olda asked. The details are Claude's proposal; built in Phase 1 with the lists.

- **Picker:** the 12 preset swatches above, plus "Custom" with a hex field (#RRGGBB, with or without the #). An invalid code shows an error and can't be saved.
- **Preview:** the picker shows the colour on a light and a dark card before saving. If text in that colour would fall below 4.5:1 contrast, it warns but still allows it.
- **Dark mode:** presets have their own dark shade. For a custom colour the app works out a lighter shade for dark mode and a darker text shade for light mode; a second hex code for dark mode can be set if the automatic one looks wrong.
- **Priority presets:** p1 Red, p2 Orange, p3 Blue, p4 no colour (secondary text); each can be changed in Settings → Colours.
- **Defaults:** a new list takes the next preset in order; a new tag takes Slate.
- **Storage:** colours are saved as hex values, so custom ones survive updates. None of them sync to Google Tasks.

## Roadmap

| Phase | Contents | Done when | Rough effort |
| --- | --- | --- | --- |
| 0 · Setup and look | Tools, empty app on the phone, GitHub; pick the look | Opens on the phone; look chosen | 1 weekend |
| 1 · Core app, no sync | Groups, lists, headings, Today, Upcoming, Inbox; add, edit, complete, search | Used daily beside TickTick for a week | 2–3 weekends |
| 2 · Czech and English quick-add | Parser, live highlighting, 100+ phrase test suite | Nearly all real captures parse correctly | 1–2 weekends |
| 3 · Reminders and repeats | Notifications with Done/Snooze; recurring tasks; hide recurring | A week with no missed reminder | 1 weekend |
| 4 · Google Tasks sync | Cloud project, sign-in, import, two-way sync, time in notes | A week, nothing lost or duplicated; tasks in Calendar | 2–4 weekends |
| 4b · Share with Do-day | Tasks and the Inbox shared with Do-day; Inbox goals (every week or month, N times, with a length) | Do-day shows today's tasks, and a task ticked in either app is done in both | 1–2 weekends |
| 5 · Focus timer and time tracking | Pomodoro and stopwatch, sessions per task and list, stats | A week of focus time tracked in the app | 1–2 weekends |
| 6 · Switch and polish | TickTick import, backups, widget, quick-add from anywhere | TickTick uninstalled | ongoing |
| 7 · Long-term planning | Yearly goals and big projects, local only; tasks link to them | Scope set once Phase 6 is in use | later |
| 8 · Windows app | Shared Kotlin Multiplatform module, desktop screens, browser sign-in, sync while open, Windows notifications | The same tasks on the PC as on the phone for a week | 3–5 weekends |

## Later: sharing with Do-day

Decided on 24 November: the Inbox lives in this app, and Do-day (`oldrichfrydrych-ux/calendar`, spec in its `docs/design.md`) reads it. Phase 4b builds the sharing.

- **What Do-day sees:** tasks with a date (title, date, time, length, list colour, done), Inbox items without a date, and Inbox goals.
- **Inbox goals:** a new kind of Inbox item that should happen every week or every month a number of times, with a length, such as "Běh, 2× týdně, 45 min". Google Tasks sees it as an ordinary undated task with a line in its notes. Do-day counts progress from the events it plans for it.
- **How:** a content provider in this app, protected by a signature permission, so only apps signed with Olda's key can read it. Both apps use the debug key on Olda's PC and must share one release key later.
- **Writing back:** ticking a task in Do-day marks it done here, through the same provider.
- **Until then:** Do-day works against its own stand-in, so neither app waits for the other.

## Later: long-term planning

Phase 7 adds a space for yearly goals and big projects that stays on the phone and never syncs to Google Tasks. Two small choices are made now so Phase 1 needs no rework; everything else waits until the prototype has been in daily use.

- **What it holds:** goals (for example yearly) and big projects with milestones. Everyday tasks can belong to a goal or project.
- **Storage:** only in the app's own database, covered by the same backup as tags and priorities.
- **Decided now:** every task has an optional goal or project link from the first database version, and the navigation leaves room for the module (inside Lists at first; it can become a fifth tab).
- **Decided later:** its screens, timeline and milestone views, periodic reviews, and how goals break down into tasks.

## Later: Windows app

Phase 8 brings the app to Windows with Kotlin Multiplatform and Compose Multiplatform (Windows 10 and 11). Windows' own way of running Android apps ended in March 2025, so this is the route.

- **Shared:** the database, the parser, Google Tasks sync and most screens and theme.
- **Windows-only:** signing in to Google through the browser, syncing on a timer while the app is open (WorkManager is Android-only), Windows notifications, and a layout that uses a wide window.
- **Decided now:** the core stays free of Android-only APIs from Phase 1 on (see Stack), so Phase 8 is mostly a move plus new screens, not a rewrite.
- **Decided later:** window layout, keyboard shortcuts, whether the companion calendar comes to Windows too.

## Answers and builder's calls

- **Sync account:** Olda's personal Google account.
- **Languages:** tasks are typed in a mix of Czech and English.
- **Computer:** has at least 8 GB of RAM.
- Open: name of the app (not urgent).
- Open: how long-term planning relates to Seiza (Olda's planned project-planning app): replace it, link to it, or keep them separate (before Phase 7).

**Left to the builder:** library choices (HTTP client, dependency injection), the exact database schema, the background sync interval, and colour values once the Phase 0 look is chosen.
