# Phase 2 · Czech and English quick-add — Design

Nov 27, 2026 · for Olda's task app
Spec this serves: `docs/design.md`, section "Czech and English quick-add"
Phase 1 shipped and is in daily use with 31 real tasks on the phone.

## Intent and why

One typed line becomes a finished task. Type `zitra ve 3 zavolat na urad #prace`
and get a task tomorrow at 15:00 in the Práce list, titled `zavolat na urad`.

This is the reason the app exists. `docs/design.md` names the anti-goal plainly:
"Capturing a task is slower than in TickTick." Phase 1 made capture work — a sheet
with a date chip, a list chip and a priority chip — but it is three taps and a
scroll behind simply typing the sentence. TickTick has no natural-language input at
all, which is why Olda is building this; Phase 2 is where the app earns its
replacement.

It is also the phase that is cheapest to get right and most expensive to get wrong
later. The parser is pure Kotlin over a string and a reference time, so it is
testable entirely on the PC, and `docs/design.md` asks for a suite of 100+ real
phrases in both languages. Everything after this phase — reminders, repeats, sync —
reads the fields the parser fills.

**Done when:** Olda types `zitra ve 3 zavolat na urad #prace` into the app on his
phone and gets a task on tomorrow at 15:00 in Práce titled `zavolat na urad`; the
JVM phrase suite passes; the device tests pass; and his 31 real tasks come through
the database migration unchanged.

## Goals

- **One line, one task.** Dates, times, relative dates, repeats, a list, tags and a
  priority are all recognised in a single typed line, anywhere in it, in Czech or
  English or both at once.
- **Diacritics optional.** `zitra` works exactly like `zítra`. Olda types without
  them on the phone.
- **The recognised words leave the title.** `zitra ve 3 zavolat na urad #prace`
  saves a task called `zavolat na urad`, not the whole line.
- **You can see what it understood before you save.** The chips under the field fill
  in and pop as you type.
- **Never worse than typing by hand.** A line the parser does not understand must
  still save as an ordinary task with the words intact. Silently re-dating or
  re-titling a task is the failure mode that matters.
- **Backed by real phrases.** 100+ cases in both languages, built from Olda's own
  tasks and his own way of phrasing a capture, run on the PC in seconds.

## Non-goals

Named because they are plausible here and deliberately out:

- **In-field highlighting and tap-to-undo.** `docs/design.md` describes recognised
  words lighting up inside the text field, with a tap turning one back into plain
  text. Olda's call on 27 Nov: the chips carry that job in this phase. The parser
  still returns the exact character range of every match, so the highlighting is a
  UI change later and not a parser change.
- **Acting on a repeat.** `každý den` is recognised and stored in this phase.
  Creating the next task when one is ticked, the repeat icon on rows and the "hide
  recurring" switch are Phase 3, as the roadmap has it.
- **A tag screen.** Tags become real in this phase — created, attached, shown and
  removable on a task's detail — but there is no screen listing all tags, no
  renaming, no tag colours beyond the default, and no filtering a view by tag.
- **Parsing anywhere but the quick-add sheet.** Editing a title in task detail stays
  literal: Olda must be able to write `Číst při Týdenním resetu (pondělí)` into a
  title without it turning into a date.
- **Quick-add from outside the app** (share sheet, widget, keyboard) — Phase 6.
- **Google sync, reminders, the Day view.** Other phases. Still no `INTERNET`
  permission.
- **New libraries.** Nothing is added. Everything needed is already in the build.

## Anti-goals

It has failed, even if every test passes, if:

- A task Olda captures gets a date or a time he did not type.
- Words he meant as the title disappear from it.
- A typo in `#list` quietly leaves a junk list behind.
- Adding a phrase the parser missed means understanding the whole parser.
- His 31 real tasks come out of the migration changed.

## Constraints

- **Platform-free core.** The parser lives in `data/parser/`, inside the package
  `DataLayerIsPlatformFreeTest` already guards: no `android.*` imports, no
  `Context`. Phase 8 moves it to a shared Kotlin Multiplatform module. `java.time`
  is allowed and already used in `data/`.
- **A real database with real data in it.** `tasks` is at version 1 with Olda's 31
  tasks and no destructive-migration fallback. The one schema change in this phase
  is additive and gets a migration test against the exported `schemas/1.json`.
- **Czech morphology, not just vocabulary.** `v pondělí`, `ve středu`, `za 3 dny`,
  `za 5 dní`, `každé pondělí` — one concept has several written forms, and the
  parser has to accept the ones a person actually types.
- **His own phrasing anchors the suite.** His 14 live task titles are in the
  repository already (read from a database backup, see Appendix A); they are the
  negative cases, and they are not negotiable. The positive phrases were asked of
  him twice on 27 Nov and had not arrived when the plan was written, so the
  builder's call: the builder writes the 100+ from the rule tables below, using
  Olda's own vocabulary from Appendix A, and the last task of the phase is Olda
  reading the list and adding what it is missing. Each phrase he adds is one row.
- **Compose tests type without the system keyboard.** `performTextInput` sets the
  field's text directly rather than going through the IME, so the quick-add device
  tests do not depend on Olda's own debug-build keyboard.
- Package `cz.olda.tasks`, minSdk 29, one `app` module, Gradle Kotlin DSL. Code
  identifiers in English; user-facing text only in `res/values/strings.xml`.

## Approaches

Three ways to turn a line into fields. Olda picked **A** on 27 Nov.

| | What it is | Trade-off | Verdict |
|---|---|---|---|
| **A. A table of patterns** | One small file per family of phrases (days, weekdays, calendar dates, times, relative, repeats, symbols). Each family is a list of patterns with the meaning beside it. A resolution step picks the winners when two patterns claim the same words. | Two patterns can claim the same words, so the resolution step needs rules and tests of its own. | **Picked** |
| **B. A word-by-word scanner** | Walk the line one word at a time, asking each recogniser "does something start here?". | Overlaps cannot happen, and it is easy to step through. But every new phrase means touching the walking code, and Czech inflection makes that verbose fast. | Rejected |
| **C. An off-the-shelf date parser** | A library. | None handles Czech, which is the main language here. `docs/design.md` settled this already, and CLAUDE.md forbids a new library without asking. | Ruled out before the conversation |

**Why A, in Olda's words:** this feature will be used, will miss a phrase, and the
fix should be one new line in a table plus one new test row — for months, not just
during this phase.

## How it works

### The shape of it

```
  "zitra ve 3 zavolat na urad #prace"          what Olda typed
            │
            ▼  fold            one character in, one character out
  "zitra ve 3 zavolat na urad #prace"          lower case, no diacritics, same length
            │
            ▼  scan            every pattern family offers candidates
  [ DATE "zitra" 0..5 ]  [ TIME "ve 3" 6..10 ]  [ LIST "#prace" 27..33 ]
            │
            ▼  resolve         longest first, then leftmost; one value per field
            │
            ▼  apply           against "now"
  date = tomorrow   minute = 15:00   list = "prace"
            │
            ▼  cut             matched ranges removed from the ORIGINAL line
  title = "zavolat na urad"
```

Because the fold is one character in and one character out, a range found in the
folded text is the same range in what Olda typed. That is why it is a fold and not
the existing `normalise()`, which uses NFD and changes the length.

### What the parser is

A single pure function in `data/parser/`:

```kotlin
fun parseQuickAdd(
    input: String,
    now: LocalDateTime,
    knownLists: List<String>,   // the existing lists' names, as stored
): ParsedInput
```

`knownLists` is there because three of the `#` rules cannot be decided without it:
whether `#pra` is a unique prefix, whether `#s` is ambiguous, and whether an unknown
`#xyz` stays in the title. A parser that did not know the names would have to leave
that decision to the sheet, which would move title-cutting out of the JVM suite and
split it across two places. Plain strings keep the function pure, platform-free and
fully testable on the PC: the suite passes his five list names as a list of strings.
The parameter is required, not defaulted, so no caller can quietly lose `#` matching.

```kotlin
enum class TokenKind { DATE, TIME, LIST, TAG, PRIORITY, RECURRENCE }

/** Where a recognised phrase sat in the original input; Phase 3 highlights these. */
data class ParsedToken(val start: Int, val end: Int, val kind: TokenKind, val text: String)

data class ParsedInput(
    val title: String,              // the line with every recognised range removed
    val dueDate: LocalDate? = null,
    val dueMinute: Int? = null,     // minutes since midnight, as TaskEntity stores it
    val listName: String? = null,   // as typed, folded: "prace". Not an id.
    val tagNames: List<String> = emptyList(),
    val priority: Int? = null,      // 1..4; null means "not said"
    val recurrence: String? = null, // an RRULE fragment, see Repeats
    val tokens: List<ParsedToken> = emptyList(),
)
```

It returns **names, not ids**. Matching `prace` to the Práce list needs the
database, which the parser cannot see; that resolution belongs to the quick-add
sheet. This is what keeps every parsing rule testable on the PC.

`now` is a `LocalDateTime`, passed in, never read from the clock inside — so a test
can say "it is Wednesday at 16:30" and get a deterministic answer.

### The rules

**Everything matches on word boundaries only.** A pattern must be preceded by the
start of the line or a non-letter, non-digit, and followed by the end of the line or
a non-letter, non-digit. So `p1` in `tisknout p1` is a priority, but `p1` inside
`typ1a` is not, and the `@` in an email address cannot start a tag.

**A bare number is never a date or a time.** `koupit 3 rohliky` is a shopping task,
not 15:00. A number becomes a time only with `v` / `ve` / `at` in front of it, or a
colon, or `am` / `pm`, or `h`. It becomes a date only with the dots Czech writing
carries (below).

**A bare weekday is never a date** (Olda's call on 27 Nov, from his real task
`Číst při Týdenním resetu (pondělí).`). A weekday counts only after `v`, `ve`,
`on`, `this`, `tento`, `tuto`, `příští`, `pristi` or `next`. `dnes`, `zítra` and
`pozítří` still work on their own: nobody writes those by accident.

**Longest wins, then leftmost.** Candidates are sorted by length descending, then by
start ascending, and accepted if they do not overlap something already accepted.
A date or time pattern absorbs its own leading preposition into its range
(`ve 3. 12.` is one nine-character date, not `ve 3` plus an orphan `12.`), so the
title never keeps a dangling `v`.

**First wins per field; the loser stays in the title.** `dnes zitra zavolat` takes
`dnes` and leaves the word `zitra` in the title, visible, rather than silently
picking one of two dates. Only tags accumulate; everything else holds one value.

**A token may fill two fields.** `za hodinu` sets both the date and the time, and is
accepted only if both are still free.

### Dates

| Typed | Means |
|---|---|
| `dnes`, `dneska`, `today` | today |
| `zítra`, `zitra`, `zejtra`, `tomorrow` | today + 1 |
| `pozítří`, `pozitri`, `day after tomorrow` | today + 2 |
| `v pondělí`, `ve středu`, `on monday`, `this friday` | the nearest such day, **today included** |
| `příští pátek`, `pristi patek`, `next friday` | that weekday in the **following** week (weeks start Monday) |
| `24. 12.`, `24.12.`, `24. 12` | 24 December |
| `24.12.2026`, `24. 12. 2026` | with the year as given |
| `24. prosince`, `24. prosinec`, `24 December`, `Dec 24` | by month name, Czech genitive or nominative, English full or three-letter |
| `za 3 dny`, `za 5 dní`, `za den`, `in 3 days`, `in a day` | today + N |
| `za 2 týdny`, `in a week`, `za měsíc`, `in 3 months`, `za rok` | today + N weeks / months / years |

Weekday semantics are Olda's call on 27 Nov: on a Monday, `v pondělí` is today; on
a Wednesday, `příští pátek` is the Friday of next week, because the Friday in two
days is simply `v pátek`.

A date with no year takes **this year if that day is today or still ahead, otherwise
next year** — so `24. 12.` typed on 30 December means next December. An impossible
date (`31. 2.`) matches nothing and stays in the title.

### Times

| Typed | Means |
|---|---|
| `ve 3`, `at 3`, `v 15` | 15:00 — **hours 1 to 7 read as afternoon**, 0 and 8 to 23 as typed |
| `v 15:30`, `15:30`, `ve 3:30` | that time; a colon needs no preposition |
| `v 15.30`, `at 15.30` | the same — see the dot rule below |
| `v 15h`, `ve 3 hod` | that hour |
| `3pm`, `at 3:30 pm`, `7am` | as written; `am` / `pm` need no preposition |
| `ve 3 ráno`, `at 3 am`, `ve 3 dopoledne` | 03:00 — an explicit morning marker turns the afternoon rule off |
| `ráno`, `morning` | 08:00 |
| `dopoledne` | 10:00 |
| `v poledne`, `noon` | 12:00 |
| `odpoledne`, `afternoon` | 15:00 |
| `večer`, `evening` | 19:00 |
| `dnes večer`, `tonight` | today, 19:00 |
| `v noci`, `night` | 22:00 |
| `za hodinu`, `za 2 hodiny`, `in an hour`, `za 20 minut`, `in 30 min` | now + that much, to the minute, setting the date too |

The named hours are Olda's pick on 27 Nov: 8 · 10 · 12 · 15 · 19 · 22.

**The dot rule.** A numeric date is written with the dots Czech writing gives it: a
trailing dot (`24. 12.`, `24.12.`) or a year (`24.12.2026`). A time may use a colon
or a dot between hours and minutes (`15:30`, `15.30`). So `15.30` is half past three
and `15. 3.` is the fifteenth of March, and the difference is the final dot —
exactly as it is on paper. `24.12` with no trailing dot and no year is recognised as
neither and stays in the title.

**A time with no day lands on the next one** (Olda's call on 27 Nov): if the hour has
already passed today, the task goes to tomorrow. `ve 3 zavolat` typed at 16:30 is
tomorrow at 15:00. But a day said out loud always wins — `dnes ve 3` at 16:30 is
today at 15:00, overdue on purpose, because he typed `dnes`.

### Repeats

Recognised and stored; nothing acts on it until Phase 3.

| Typed | Stored |
|---|---|
| `každý den`, `kazdy den`, `denně`, `every day`, `daily` | `FREQ=DAILY` |
| `každé pondělí`, `kazde pondeli`, `every monday` | `FREQ=WEEKLY;BYDAY=MO` |
| `každý týden`, `every week`, `weekly` | `FREQ=WEEKLY` |
| `každý měsíc`, `měsíčně`, `every month`, `monthly` | `FREQ=MONTHLY` |
| `každý rok`, `ročně`, `every year`, `yearly` | `FREQ=YEARLY` |
| `každé 2 dny`, `every 3 days`, `každé 2 týdny` | `FREQ=DAILY;INTERVAL=2` etc. |

`každý`, `každá`, `každé` and their diacritic-free spellings are all accepted.

**The stored form is an RRULE fragment** (RFC 5545), the builder's call: Google Tasks
uses the same vocabulary, so Phase 4 can hand it over unchanged, and Phase 3 can add
`UNTIL` or `COUNT` without another schema change.

**A repeat with no date sets one** — the builder's call, because a repeat with no
first occurrence cannot be acted on in Phase 3. `každé pondělí` alone means the
nearest Monday (today included, the same rule as `v pondělí`); `každý den` alone
means today.

**A date that came from a repeat rolls forward if its time has already gone** — the
builder's call after the design review, because the two rules above otherwise
collide with the "a day said out loud always wins" rule below. A date Olda typed is
never moved. A date the repeat implied is moved on by exactly one period of that
repeat when the resulting moment is already past:

| Typed at | Means |
|---|---|
| `kazdy den ve 3` at 16:30 | tomorrow 15:00, repeating daily (one day on) |
| `kazde pondeli v 9` on Monday at 11:00 | next Monday 09:00 (one week on) |
| `kazdy den ve 3` at 11:00 | today 15:00 |
| `dnes kazdy den ve 3` at 16:30 | today 15:00 — he typed the day, so it stands |
| `kazdy den` at any time | today; with no time there is nothing to have missed |

### The symbols

| Typed | Means |
|---|---|
| `#prace` | the list whose folded name is `prace` |
| `#pra` | the same, if exactly one list's folded name starts with `pra` |
| `#xyz` | nothing: the word stays in the title and the task goes to Inbox |
| `@telefon` | the tag `telefon`, **created if it does not exist** |
| `p1` … `p4`, `P1` … `P4` | priority 1 to 4 |

Olda's call on 27 Nov: **lists must already exist, tags are made on the spot.** A
mistyped `#` must not leave a junk list behind, and he has few lists and knows them;
tags are throwaway by nature and he has none yet, so requiring them to exist first
would mean `@telefon` never works.

A `#` or `@` name is letters, digits, `_` and `-` — no spaces, so a multi-word list
is reached by a unique prefix (`#osob`). An ambiguous prefix (`#s` against both Škola
and Someday) matches nothing and stays in the title, like an unknown name. Matching
is done over all lists in memory on the folded name, so no normalised column is
needed. A second `#` is ignored and left in the title; several `@` tags all apply.

**His list names start with an emoji, and that has to be handled in the matching.**
His real rows are `☀️Osobní`, `🌊Práce`, `🎓Škola`, `🧊Someday` — the emoji is inside
`name`, with the `icon` column unused since Phase 1 (`ListsScreen.kt` says as much,
and `FirstCharacterTest` exists because of it). Folded, `🌊Práce` is `🌊práce`, which
does not begin with `prace`, so a naive prefix match would fail the acceptance
criterion of this whole phase. The builder's call, after the design review on 27 Nov:
**a list name is folded for matching and then every character that is not a letter,
a digit, a space, `_` or `-` is dropped, and the result trimmed** — so `🌊Práce`
matches as `prace`. Nothing is written to the database and nothing on screen changes.

Moving the emoji into the `icon` column where it belongs would be the tidier fix, but
it is a data migration plus a change to how every list tile and chip renders, which
is a cleanup of Phase 1's work and not this phase's job. Worth doing on its own
later.

### The title

The title is the original line with the accepted ranges cut out, runs of whitespace
collapsed, a space before `,` `.` `!` `?` removed, and the result trimmed. **The
parser never rewrites Olda's words** — it only removes them. `zitra ve 3 zavolat na
urad` gives the title `zavolat na urad`, without diacritics, because that is how he
typed it. (`docs/design.md` writes that example with diacritics; restoring them is
not something a parser can do, and guessing would be worse than leaving his words
alone.)

If nothing is left of the title, the parse still shows on the chips but Save stays
disabled, which is Phase 1's existing rule for a blank title.

### What the quick-add sheet does with it

The sheet keeps the raw line in the field; the parse runs on every keystroke. The
parsed values flow into the chips, which gain two new ones:

```
┌─────────────────────────────────────────────────────┐
│  zitra ve 3 zavolat na urad #prace @telefon p2      │
│                                                     │
│  [ pá 28. 11. ] [ 15:00 ] [ 🌊 Práce ] [ p2 ]       │
│  [ @telefon ]                                       │
└─────────────────────────────────────────────────────┘
      ▲          ▲                                     each fills in and pops
   existing    new: time, repeat, tags
```

- **A chip you touch is yours.** Tapping the date chip and picking a day pins that
  field: further typing no longer changes it, so the parser cannot undo a correction
  mid-sentence. Clearing the field unpins everything. The pins live in the saved
  state with the draft, so a process death does not lose them.
- **Pinning changes the value, never the cut.** The builder's call after the design
  review: a recognised range leaves the title whatever the chips say afterwards. Type
  `zitra zavolat`, tap the date chip, pick Friday, and the task is `zavolat` on
  Friday — not `zitra zavolat` on Friday, which is the only other reading and a worse
  one. The word was typed as a date, which is why it matched; correcting the date does
  not turn it back into a title. The cost of this, and of leaving tap-to-undo to a
  later phase, is that a word the parser took and Olda wanted in the title has to be
  typed again. The chips make that visible before saving, which is why they are in
  this phase at all.
- **The repeat chip appears only when a repeat was recognised**, and echoes the words
  Olda typed — `↺ kazdy den`, in his own spelling. The builder's call: those are his
  words and so they are data, not interface text, which keeps CLAUDE.md's rule that
  user-facing strings live in `strings.xml` intact without a Czech string file (there
  is no `values-cs`; the interface is English today). Tapping the chip clears the
  repeat; there is no repeat picker in this phase.
- **The time chip** shows the parsed time and opens a time picker; the field finally
  reaches the database, which Phase 1 left as a hard-coded `null`.
- **Saving** writes the cut title, the date, the time, the priority, the recurrence
  and the list, then attaches the tags, creating any that are new.
- **The pop** reuses the existing spring vocabulary in `ui/theme/Motion.kt`; with
  Android's "remove animations" on, the chips simply change.

### What changes in the database

One additive column:

```kotlin
// TaskEntity
val recurrence: String? = null   // an RRULE fragment; Phase 3 acts on it
```

`AppDatabase` goes to version 2 with one migration:

```sql
ALTER TABLE tasks ADD COLUMN recurrence TEXT DEFAULT NULL
```

registered in `AppContainer`, which is the only place that builds the database.
`Migration` and `SupportSQLiteDatabase` are `androidx.room` and `androidx.sqlite`,
not `android.*`, so `data/` stays platform-free. A `MigrationTestHelper` test runs
version 1 from the committed `schemas/1.json`, writes a task, migrates, and asserts
the task is intact with `recurrence` null — the test that stands between a migration
and Olda's 31 real tasks. The new `schemas/2.json` is committed.

**A repeat has to be removable.** `TaskDao.updateEditableAndFollow` writes a fixed
set of columns, so unless `recurrence` joins them, a repeat typed by mistake could
only be undone by deleting the task — shipping a field with no way out. The builder's
call after the design review: `recurrence` joins the editable columns, and task
detail shows the repeat with an × that clears it. **Changing** a repeat to a different
one is Phase 3's job, with the picker it builds anyway; this phase only has to let
Olda get rid of one. Task detail renders the repeat from the stored rule through
`strings.xml` ("Every day", "Every Monday"), because by then his original words are
gone and there is no Czech string file to say it in.

**Tags need their plumbing built**, not their schema: `tags` and `task_tags` have
existed since Phase 1 and nothing has ever used them. This phase adds `TagDao`, a
small `TagRepository` (find-or-create by folded name, attach, detach, observe a
task's tags), the default Slate colour from `docs/design.md`, and the tag row on
task detail. `tags` has no unique index on `name`, and adding one is a second schema
change, so `TagRepository` does the de-duplicating; the quick-add save already runs
under a mutex, so there is one writer at a time.

## What is left to the builder

- The exact regular expressions, how the pattern files are split, and the internal
  candidate type.
- The order of the pattern families and how resolution is implemented, as long as
  "longest wins, then leftmost" holds and is tested.
- Whether `TagRepository` is a new class or methods on `TaskRepository` (a new
  class is the starting assumption: `TaskRepository` is already large).
- The exact look of the tag row on task detail and of the new chips, within the
  Look section of `docs/design.md`.
- The strings for the new chips, in `strings.xml`.
- How the 100+ phrase suite is laid out, as long as one row is one phrase and
  adding a phrase is adding a row.

## The calls made after the design review

A fresh engineer read this document on 27 Nov and reported seven places where they
would have had to guess. All seven are settled above; these are the answers, so Olda
can check the reasoning rather than hunt for it:

1. **The parser takes the list names as a third argument.** Without them, half the
   `#` behaviour could not be decided inside the parser, and title-cutting would have
   split across two places.
2. **`#` matching ignores the emoji in a list name.** His names carry it inside
   `name`, not in `icon` as this document first assumed. Without this fix the phase's
   own acceptance criterion fails on his phone.
3. **The builder writes the 100+ phrases; Olda reviews them last.** He was asked
   twice and the plan should not wait on an answer.
4. **Pinning a chip changes that field's value and never the title cut.**
5. **A repeat-derived date rolls forward one period if its time has passed**; a date
   he typed never moves.
6. **`recurrence` becomes an editable column and task detail can clear a repeat.**
   Changing one waits for Phase 3's picker.
7. **The quick-add repeat chip echoes his own words; task detail says it in English**
   from the stored rule, because by then his wording is gone.

## Deliberately left out, cheap to add later

Each of these is one row in a pattern table plus one test row, once Olda hits it:

- `v půl čtvrté`, `v půl 4` (half past three) and the other `půl` + ordinal forms.
- `24.12` and `24/12` without the trailing dot.
- `za 3 pracovní dny` (working days), week numbers, `o víkendu` / `at the weekend`.
- `včera` / `yesterday`.
- **`týdně` on its own** (it was in the table above until the code review on 27 Nov). The fold
  strips the length mark, so it is the same word as `týdne`, the genitive of *týden* — and "do
  konce týdne uklidit" was being read as a weekly repeat with the words taken out of the title.
  `každý týden` says it unambiguously. `denně`, `měsíčně` and `ročně` have no such twin and stay.
- **`od`, `kolem`, `okolo` in front of a time.** They were implemented and removed for the same
  reason: the calendar-date patterns do not absorb them, so "Dovolena od 3. 12." read `od 3` as
  three o'clock and lost the date. Adding one means teaching both families about it at once.
- **`na`, `do`, `k`, `po` in front of a date or time** are not absorbed either, so "poslat report
  do 15:00" keeps its "do" in the title. The time is still right.
- Czech weekday abbreviations (`po`, `st`, `čt`) — `po` is also the word "after",
  so these need more care than they are worth now.

Two larger ones, each its own piece of work rather than a table row:

- **Changing** a repeat on an existing task, rather than only clearing it — Phase 3,
  which builds the picker anyway.
- **Moving the emoji out of each list's name into the `icon` column**, where Phase 1
  meant it to live. A data migration plus a change to every list tile and chip; a
  cleanup of Phase 1, not part of this phase.

## Appendix A · the negative cases

Every one of Olda's live task titles, read from the database backup on 27 Nov, must
come out of the parser **unchanged, with no date, time, list, tag, priority or
repeat**. They are the suite's regression test against an over-eager pattern:

```
Číst při Týdenním resetu (pondělí).        ← the title the bare-weekday rule exists for
Koupit zimní kabát
Napsat na ekonomku
Odevzdat Martině dokumentaci AUI
Opravit Fluxus automatizace
Udělat auditní scrapper
Vyzkoušet facebook MUSE
Vyzkoušet speech stuff s ai
Fluxus: export PDF reportu
Aplikace na tvorbu formulářů.
Alea, Více rolí?
Penzijní spoření
napsat katce Kobušové -
Zastav se pro přítomný okamžik.
- I have no enemies, even if the enemy is myself.
```

His lists, for the `#` cases: Inbox, ☀️ Osobní, 🌊 Práce, 🎓 Škola, 🧊 Someday.
