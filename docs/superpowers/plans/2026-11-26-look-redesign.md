# Look redesign — finish the chosen direction

**Goal:** Make the app look like the direction Olda chose on 23 November, the "Chosen direction"
page of https://claude.ai/artifact/7KbFvurP9WWc49FXhS2A2j — Today's List view and the frame around
it — and carry that look through every other screen.

**Where the look comes from:** the mockups' List-view artboard (`F-Today`, light and dark) and the
Lists artboard of the same direction (`R1-Lists`). `docs/design.md`, section *Look*, is the written
version and is already the source of the palette, Manrope and the floating bar, which landed in
Phase 0 and 1.

**What was already right before this plan:** the warm palette (`Color.kt`), Manrope at every size
(`Type.kt`), and the floating dark bar with its coral add button and travelling pill — the mockups'
bar, built in Phase 0 and animated since. None of that is touched here.

## Scope, decided with Olda on 26 November

**In:** Today's List view and its frame, and the same look on Upcoming, Inbox, Lists, list detail,
search and task detail.

**Out, and why:**

- **The Day view** (time grid, blocks sized by duration, free slots as drop targets). It is not a
  look, it is a feature: nothing in the app writes `tasks.durationMinutes`, so there are no block
  heights, no free gaps and nothing to drag. Its own plan, later. The List/Day switch is drawn,
  because it is part of the header's shape, with Day visibly disabled rather than silently dead.
- **"2 h 30 free"** in the glance line, for the same reason: free time cannot be computed without
  durations. The done count beside it is real and stays.
- **Pinned lists** (the four big circles above Lists) and **per-list progress rings**. Pinning is a
  feature nobody asked for yet; the ring needs a done-vs-open ratio per list, which is a query and a
  decision about what counts as "done" over what period.
- **Per-list icons.** `lists.icon` exists and nothing writes it, so every tile would show the same
  glyph. The tile carries the list's colour instead, which is the list's identity here.

## Global constraints

- No new libraries, no dependency version changes (CLAUDE.md: ask Olda first).
- **No `@Entity` changes.** The schema is version 1, there is no destructive-migration fallback and
  Olda's real tasks are in the database. New DAO queries are fine; new columns are not.
- User-facing text only in `res/values/strings.xml`; code identifiers in English.
- `data/` stays free of `android.*` imports and `Context` (`DataLayerIsPlatformFreeTest` guards it).
- Every animation already in place keeps working, and still collapses to nothing when
  `LocalAnimationsEnabled` is false.
- **`TaskRow` keeps deciding its layout from measured width**, not from font scale, and its
  large-font tests keep passing unchanged. `docs/phase-1-handover.md` says why; a Czech word that
  cannot wrap is the reason.
- **`textShade()` stays.** Text in a list's colour keeps 4.5:1; the ring and the dot may use the raw
  colour, text may not.
- A task is done when `gradlew.bat testDebugUnitTest` passes and, with the phone connected,
  `gradlew.bat connectedDebugAndroidTest` passes.

## Task 1 · The task row becomes the rail row

The mockups' rows sit straight on the warm background: no white card, a ring in the list's colour
for the checkbox, the time in a narrow column before the title, the list's name behind it. Today the
row is a white `Surface` with a Material `Checkbox`, which reads as a form rather than a rail.

- `TaskRow`: drop the `Surface` and its 16dp corners; keep the whole `BoxWithConstraints` width
  maths, the three layouts and every test tag.
- The checkbox becomes a ring: a 16dp circle, 3dp border in the list's raw colour, inside a 48dp
  touch target, filled with a tick once done. It keeps `testTag("task-checkbox-<id>")`, the
  `R.string.complete_task` description and `Role.Checkbox` with a toggleable state, so every
  existing test and TalkBack still find it.
- Title `bodyLarge` at `FontWeight.Bold`, time `labelLarge` on secondary text, list name unchanged
  (`textShade`).
- The rail line itself belongs to the section, not the row (Task 2): a row knows nothing about its
  neighbours.

**Tests:** `TaskRowTest` keeps its large-font and layout tests; add that the ring carries the
complete action and that a done row shows its tick. No card means the old "row is a surface" sort of
assertion, if any, goes.

## Task 2 · Today's frame

- **Header:** title and the day beside it (`Today · Mon 23 Nov`), then the List/Day switch at the
  trailing edge. `ScreenHeader` gains an optional subtitle so every screen can use the same header;
  the switch is Today's own composable, with Day `enabled = false`.
- **Glance:** a proportional bar of list colours over today's open tasks, and under it "N of M
  done". M is today's open plus done; the done count is a new `TaskDao` count query and a new field
  on `TodayState`.
- **Rail and now line:** a 2dp vertical line behind the checkbox column, and, only while the shown
  day is today, a now row — the time in the time column, a line across the rest — placed between the
  timed tasks it falls between. The current time is a parameter of the content composable, so a test
  can pin it.

**Tests:** `TodayViewModelTest` for the done count; `TodayScreenTest` for the switch being there and
Day being disabled, the glance line's text, and the now row landing between the right two tasks.

## Task 3 · The week strip

The strip is right in shape and wrong in colour: the shown day is coral and the other six are white
cards. In the mockups the shown day is a dark ink pill with white text, the rest sit on the
background with no fill at all, and the weekend is muted.

- Shown day: ink pill (`onBackground`), text `background`. Others: no fill, `onBackground`, weekend
  `onSurfaceVariant`.
- Up to four dots, 5dp.

**Tests:** `WeekStripTest` keeps its tags; add that only the shown day has a fill.

## Task 4 · The Tomorrow row

Today currently lists tomorrow's tasks in full under a "Tomorrow" header, which buries the end of
today. The mockups put one row at the bottom: `Tomorrow · Tue 24`, the count, a chevron.

- One surface row, 14dp corners, tapping it shows tomorrow (the strip's own `onPickDay`), hidden
  when tomorrow has nothing and when the shown day is not today.

**Tests:** `TodayScreenTest` — the row shows the count, is absent when tomorrow is empty, and moves
the day when tapped.

## Task 5 · The other screens

- **Lists:** rows as 18dp cards with a 38dp tinted tile in the list's colour, the name, and "N open"
  under it. Search stays in the header, new list and new group stay where they are.
- **Upcoming, Inbox, search, list detail, task detail:** the new `ScreenHeader` with its subtitle
  where there is one to show, and the 20dp side padding the mockups use throughout, so the rail
  lines up with the header.

**Tests:** the existing screen tests keep passing; `ListsScreenTest` gains the subtitle.

## Calls made while building this

- **One side margin, 20dp, for every screen including the task rows.** The mockups pull the rail
  slightly left of the text above it, so the ring's circle lines up with the header's first letter.
  Doing that meant Today's rows sitting 15dp left of every other screen's, and rows that slide sideways
  as Olda moves between tabs are more noticeable than a rail 14dp further in than the drawing.
- **The glance line counts the shown day only**, not the overdue rows above it. Overdue tasks belong
  to days that have gone, and the done count behind "of 7" is a query on that one date.
- **A letter, not an icon, in the Lists tile.** `lists.icon` exists and nothing writes it, so every
  tile would have carried the same glyph. The initial plus the list's colour identifies it.
- **The open ring is filled with the page colour**, not left transparent, because the rail line runs
  through the middle of that column and would otherwise show through the circle.

## Task 6 · [Olda] On the phone

What to look at: the rail with no cards, the ring checkboxes in list colours, the glance bar, the now
line's position, the Tomorrow row, and dark mode. Known to be missing: the Day view.
