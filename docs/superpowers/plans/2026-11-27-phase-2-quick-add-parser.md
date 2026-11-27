# Phase 2 · Czech and English quick-add — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One typed line becomes a finished task: `zitra ve 3 zavolat na urad #prace` saves a task tomorrow at 15:00 in Práce, titled `zavolat na urad`.

**Architecture:** A pure function in `data/parser/` folds the line to lowercase-without-diacritics one character at a time, each pattern family offers candidate matches with concrete values, a resolution step accepts the winners (longest first, then leftmost, one value per field), and the accepted ranges are cut from the original line to make the title. The quick-add sheet runs it on every keystroke, shows the result on its chips, and resolves list and tag *names* to ids when it saves. One additive database column stores a recognised repeat.

**Tech Stack:** Kotlin, `java.time`, Jetpack Compose, Material 3, Room (with KSP), JUnit 4, Compose UI tests, `room-testing`. **No new libraries** — everything needed is already in the build.

**Spec:** `docs/superpowers/specs/2026-11-27-phase-2-quick-add-parser-design.md` (which argues from `docs/design.md`, section "Czech and English quick-add")

## Global Constraints

- Package `cz.olda.tasks`, minimum SDK 29, one `app` module, Gradle Kotlin DSL.
- **No new libraries and no new permissions.** Still no `INTERNET` permission.
- **Ready for Windows (Phase 8):** everything in `data/`, the new `data/parser/` included, uses no `android.*` imports and no `Context`. `DataLayerIsPlatformFreeTest` enforces it on every run. `java.time` and `androidx.room` are fine; `android.*` is not.
- Code identifiers in English. Every user-facing string in `res/values/strings.xml`. There is no `values-cs`; the interface is English. A repeat chip echoing what Olda typed is data, not interface text.
- **The database holds Olda's 31 real tasks.** `connectedDebugAndroidTest` uninstalls the app and takes them with it. Before every device-test run: back up with `adb exec-out "run-as cz.olda.tasks cat /data/data/cz.olda.tasks/databases/tasks.db"` (and `-shm`, `-wal`) into the gitignored `.superpowers/sdd/db-backup/`, never `adb shell cat`; restore afterwards and check the sizes (94208 / 32768 / 16512).
- **The phone must be awake** or every Compose test fails at once with "No compose hierarchies found in the app": `adb shell input keyevent KEYCODE_WAKEUP` before each run.
- Device: `ANDROID_SERIAL="adb-FQAUKJPJVOKZVW8H-2NpXZJ._adb-tls-connect._tcp"`. `adb` lives at `~/AppData/Local/Android/Sdk/platform-tools/adb.exe` and is not on PATH. On Windows the wrapper is `gradlew.bat`.
- Commands: unit tests `gradlew.bat testDebugUnitTest`; device tests `gradlew.bat connectedDebugAndroidTest`; install `gradlew.bat installDebug`.
- Commit at the end of every task with a clear message, then push. Never commit `local.properties`, keystores or API keys.

## Review Focus

Five things the spec implies, that a reasonable person would expect to work, and that no task's own tests would otherwise reach. Each has its test in the task that owns the code.

1. **The same tag typed twice in one line** (`@telefon volat @telefon`) — `task_tags` has `primaryKeys = ["taskId", "tagId"]`, so a second insert of the same pair throws and the save fails. One tag must be attached and the task must save. → Task 10.
2. **A line of nothing but recognised words** (`zitra #prace p1`) — the title is empty, so Save stays disabled, but the chips must still show what was understood rather than the sheet appearing broken. → Task 11.
3. **A pasted multi-line or very long line** — quick-add is one field but paste does not care. Newlines count as whitespace, the title collapses to one line, and no pattern takes pathological time. → Task 1.
4. **The hour boundary and midnight.** `ve 3` typed at exactly 15:00, and `za hodinu` typed at 23:30, which must roll the date to tomorrow 00:30. → Tasks 4 and 5.
5. **The list the parser chose is deleted before Save.** Phase 1 already falls back to Inbox for a chosen list that disappears; the parsed path must do the same instead of writing a dangling `listId`. → Task 11.

---

### Task 1: The fold, the result type and the resolution step

The parser's skeleton with no pattern families yet: a line goes in, the same line comes out as the title. Everything later plugs into this.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/Fold.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/parser/ParsedInput.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/parser/Candidate.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/parser/MatchResolution.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/FoldTest.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/MatchResolutionTest.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/ParserNegativeCasesTest.kt`

**Interfaces:**
- Produces, in `Fold.kt`:
  - `fun fold(text: String): String` — lowercase, diacritics stripped, **exactly one character out per character in**, so a range in the folded text is the same range in the input. This is why it is not `data/TextNormaliser.kt`'s `normalise()`, which uses NFD and changes the length; `normalise` stays as it is for search.
- Produces, in `ParsedInput.kt`:
  - `enum class TokenKind { DATE, TIME, LIST, TAG, PRIORITY, RECURRENCE }`
  - `data class ParsedToken(val start: Int, val end: Int, val kind: TokenKind, val text: String)` — `end` exclusive, indices into the original input, `text` as typed. Nothing in Phase 2 reads `tokens`; it exists so the highlighting in a later phase is a UI change and not a parser change.
  - `data class ParsedInput(val title: String, val dueDate: LocalDate? = null, val dueMinute: Int? = null, val listName: String? = null, val tagNames: List<String> = emptyList(), val priority: Int? = null, val recurrence: String? = null, val tokens: List<ParsedToken> = emptyList())` — `dueMinute` is minutes since midnight, as `TaskEntity` stores it. `priority` is null for "not said", so the sheet keeps its own default of 4. `listName` is the entry from `knownLists` that matched, **verbatim as stored** (`"🌊Práce"`), so the sheet finds the id by string equality and never has to re-match.
- Produces, in `Candidate.kt` (all `internal`):
  - `internal data class Candidate(val start: Int, val end: Int, val kind: TokenKind, val date: LocalDate? = null, val minute: Int? = null, val listName: String? = null, val tagName: String? = null, val priority: Int? = null, val recurrence: String? = null, val rollOn: Period? = null)` — every family resolves its own values against `now` while scanning, so a candidate holds finished values, not something to interpret later. `rollOn` is set only by a recurrence candidate that supplied the date itself; see Task 6.
  - `internal val Candidate.length: Int` — `end - start`.
  - `internal fun wordPattern(body: String): Regex` — wraps `body` so it matches on word boundaries only: `(?<![\p{L}\p{N}])(?:body)(?![\p{L}\p{N}])`. Every family builds its patterns through this, which is what keeps `p1` out of `typ1a` and stops an email address's `@` starting a tag.
- Produces, in `MatchResolution.kt` (both `internal`):
  - `internal fun resolve(candidates: List<Candidate>): List<Candidate>` — sorts by length descending then start ascending, and accepts a candidate only if it overlaps nothing already accepted and every field it fills is still empty. A candidate fills a field when that property is non-null; `TAG` candidates may accumulate, every other kind holds one value.
  - `internal fun cutTitle(input: String, accepted: List<Candidate>): String` — removes the accepted ranges from the original input, collapses runs of whitespace (newlines included) to one space, removes a space left in front of `,` `.` `!` `?`, and trims.
- Produces, in `QuickAddParser.kt`:
  - `fun parseQuickAdd(input: String, now: LocalDateTime, knownLists: List<String>): ParsedInput` — folds, collects candidates from every family, resolves, applies, cuts, and maps each accepted candidate to a `ParsedToken` carrying its range, its kind and the original (unfolded) text at that range. In this task it collects from no families at all, so it returns the trimmed input as the title and nothing else.

- [ ] **Step 1: Write the failing tests.**

In `FoldTest.kt`:

```kotlin
@Test fun stripsCzechDiacriticsAndCase() {
    assertEquals("zitra ve 3 zavolat na urad", fold("Zítra ve 3 zavolat na úřad"))
    assertEquals("prilis zlutoucky kun upel dabelske ody", fold("Příliš žluťoučký kůň úpěl ďábelské ódy"))
}
@Test fun keepsOneCharacterPerCharacter() {
    listOf("Zítra", "ÚŘAD", "Číst při Týdenním resetu (pondělí).", "🌊Práce", "")
        .forEach { assertEquals(it, it.length, fold(it).length) }
}
@Test fun leavesEverythingElseWhereItIs() {
    assertEquals("#prace @telefon p1 15:30", fold("#prace @telefon P1 15:30"))
}
```

In `MatchResolutionTest.kt`, building `Candidate`s by hand:

```kotlin
@Test fun longerWinsOverEarlier()            // DATE 0..4 vs DATE 0..9 -> the 0..9 one
@Test fun leftmostWinsAtEqualLength()        // two 5-long DATE candidates -> the earlier start
@Test fun secondValueForAFieldIsRejected()   // two non-overlapping DATE candidates -> only the first accepted
@Test fun tagsAccumulate()                   // three TAG candidates -> all three accepted
@Test fun aCandidateNeedingTwoFreeFieldsIsRejectedWhenOneIsTaken()
    // an accepted TIME, then a candidate with both date and minute -> rejected
@Test fun cutRemovesRangesAndTidiesSpacing() {
    // "zitra ve 3 zavolat na urad" minus 0..5 and 6..10
    assertEquals("zavolat na urad", cutTitle(input, accepted))
}
@Test fun cutCollapsesNewlinesAndSpaceBeforePunctuation() {
    assertEquals("volat Petrovi.", cutTitle("zitra  volat\n\nPetrovi .", listOf(dateCandidate(0, 5))))
}
@Test fun cutOfEverythingLeavesAnEmptyTitle() { assertEquals("", cutTitle("zitra", listOf(dateCandidate(0, 5)))) }
```

In `ParserNegativeCasesTest.kt` — the suite's backstop, which every later task re-runs:

```kotlin
/** Appendix A of the design doc: Olda's 15 live task titles. None may gain a date, a
 *  time, a list, a tag, a priority or a repeat, and none may lose a word. */
private val realTitles = listOf(
    "Číst při Týdenním resetu (pondělí).", "Koupit zimní kabát", "Napsat na ekonomku",
    "Odevzdat Martině dokumentaci AUI", "Opravit Fluxus automatizace",
    "Udělat auditní scrapper", "Vyzkoušet facebook MUSE", "Vyzkoušet speech stuff s ai",
    "Fluxus: export PDF reportu", "Aplikace na tvorbu formulářů.", "Alea, Více rolí?",
    "Penzijní spoření", "napsat katce Kobušové -", "Zastav se pro přítomný okamžik.",
    "- I have no enemies, even if the enemy is myself.",
)
private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")
private val now = LocalDateTime.of(2026, 11, 27, 16, 30)   // a Friday

@Test fun hisRealTitlesComeOutUntouched() {
    realTitles.forEach { title ->
        assertEquals(title, ParsedInput(title = title), parseQuickAdd(title, now, lists))
    }
}
@Test fun aLongPastedLineIsNotMangledOrSlow() {
    val pasted = realTitles.joinToString("\n").repeat(20)   // ~12 000 characters
    assertEquals(pasted.replace(Regex("\\s+"), " ").trim(), parseQuickAdd(pasted, now, lists).title)
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest`. Expected: compile errors, unresolved `fold`, `Candidate`, `resolve`, `cutTitle`, `parseQuickAdd`.
- [ ] **Step 3: Implement `fold`** in `Fold.kt` as a character-by-character map: a `when` over the Czech accented letters to their base letters, anything else through `lowercaseChar()`. Not `java.text.Normalizer`, which changes the length.
- [ ] **Step 4: Implement the types** in `ParsedInput.kt` and `Candidate.kt` with the signatures above.
- [ ] **Step 5: Implement `resolve` and `cutTitle`** in `MatchResolution.kt`, and `parseQuickAdd` in `QuickAddParser.kt` collecting from no families yet.
- [ ] **Step 6: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, `DataLayerIsPlatformFreeTest` included.
- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data/parser app/src/test/java/cz/olda/tasks/data/parser
git commit -m "feat: the quick-add parser's fold, result type and match resolution"
git push
```

---

### Task 2: Days and weekdays

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/DayPatterns.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/parser/WeekdayPatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/DayPatternsTest.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/WeekdayPatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's `Candidate`, `wordPattern`, `fold`, `parseQuickAdd`.
- Produces:
  - `internal fun dayCandidates(folded: String, today: LocalDate): List<Candidate>` — `dnes`, `dneska`, `today` → today; `zitra`, `zejtra`, `tomorrow` → +1; `pozitri`, `day after tomorrow` → +2. These match bare, with no preposition.
  - `internal fun weekdayCandidates(folded: String, today: LocalDate): List<Candidate>` — a weekday **only** after `v`, `ve`, `on`, `this`, `tento`, `tuto` (the nearest such day, today included) or after `pristi`, `next` (that weekday in the following Monday-to-Sunday week). The preposition is inside the candidate's range. Czech forms: `pondeli`, `utery`, `streda|stredu`, `ctvrtek`, `patek`, `sobota|sobotu`, `nedele|nedeli`. English: full names and `mon|tue|wed|thu|fri|sat|sun`. No Czech abbreviations — `po` is also the word "after".
- `parseQuickAdd` now collects from both.

- [ ] **Step 1: Write the failing tests.** `now` is Friday 27 Nov 2026 16:30 unless a test says otherwise.

In `DayPatternsTest.kt`:

```kotlin
@Test fun theThreeDayWordsInBothLanguages()   // dnes/dneska/today -> 27th; zitra/zejtra/tomorrow -> 28th; pozitri -> 29th
@Test fun worksWithDiacritics()              // "Zítra zavolat" -> 28th, title "zavolat"
@Test fun firstDateWinsAndTheLoserStaysInTheTitle() {
    val r = parseQuickAdd("dnes zitra zavolat", now, lists)
    assertEquals(LocalDate.of(2026, 11, 27), r.dueDate)
    assertEquals("zitra zavolat", r.title)
}
@Test fun notInsideAWord()                   // "dnesek" and "zitrat" -> no date
```

In `WeekdayPatternsTest.kt`:

```kotlin
@Test fun aBareWeekdayIsNotADate() {
    assertNull(parseQuickAdd("Číst při Týdenním resetu (pondělí).", now, lists).dueDate)
    assertNull(parseQuickAdd("pondeli zavolat", now, lists).dueDate)
}
@Test fun nearestIncludesToday() {           // Monday 23 Nov 10:00, "v pondeli zavolat" -> 23 Nov
@Test fun nearestIsTheComingOne()            // Friday 27th, "v pondeli" -> 30 Nov; "ve stredu" -> 2 Dec
@Test fun pristiIsTheFollowingWeek() {       // Wednesday 25th, "pristi patek" -> 4 Dec, not 27 Nov
@Test fun pristiOnTheSameWeekdayIsSevenDaysOn()  // Monday 23rd, "pristi pondeli" -> 30 Nov
@Test fun englishForms()                     // "on monday", "next friday", "this sunday", "on fri"
@Test fun thePrepositionLeavesTheTitle() {
    assertEquals("zavolat", parseQuickAdd("v pondeli zavolat", now, lists).title)
}
@Test fun theTokensSayWhereEachPhraseWas() {
    // Nothing in Phase 2 reads these; the highlighting in a later phase does, so they are
    // pinned here, on the first family that produces any.
    val tokens = parseQuickAdd("Zítra zavolat", now, lists).tokens
    assertEquals(listOf(ParsedToken(0, 5, TokenKind.DATE, "Zítra")), tokens)
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*parser*"`. Expected: unresolved `dayCandidates`, `weekdayCandidates`.
- [ ] **Step 3: Implement both families** with the signatures above and wire them into `parseQuickAdd`.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, Task 1's negative cases still green.
- [ ] **Step 5: Commit.** `feat: the parser understands dnes, zitra and v pondeli`

---

### Task 3: Calendar dates

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/CalendarDatePatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/CalendarDatePatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's types.
- Produces `internal fun calendarDateCandidates(folded: String, today: LocalDate): List<Candidate>`, recognising:
  - `24. 12.`, `24.12.`, `24. 12` — day then month, **with the trailing dot that Czech writing gives it**. This is the dot rule: a numeric date carries a final dot or a year, which is what tells it apart from the time `15.30`.
  - `24.12.2026`, `24. 12. 2026` — with the year as given.
  - `24. prosince`, `24. prosinec`, `24 December`, `Dec 24`, `24. 12. 2026`. Czech month names in the genitive (`ledna`, `unora`, `brezna`, `dubna`, `kvetna`, `cervna`, `cervence`, `srpna`, `zari`, `rijna`, `listopadu`, `prosince`) and the nominative (`leden` … `prosinec`); English full names and `jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec`.
  - A leading `v`, `ve` or `dne` is absorbed into the candidate's range, so `ve 3. 12.` is one date and not a time plus an orphan.
  - A year that is absent means **this year if that day is today or later, otherwise next year**.
  - An impossible date produces no candidate at all.

- [ ] **Step 1: Write the failing tests.** `now` = Friday 27 Nov 2026 16:30.

```kotlin
@Test fun czechNumericForms()      // "24. 12.", "24.12.", "24. 12" -> 24 Dec 2026
@Test fun withAYear()              // "24.12.2026", "24. 12. 2026" -> 24 Dec 2026; "3.1.2027" -> 3 Jan 2027
@Test fun czechMonthNames()        // "24. prosince", "24. prosinec", "1. ledna" -> 1 Jan 2027 (next year)
@Test fun englishMonthNames()      // "Dec 24", "24 December", "dec 24" -> 24 Dec 2026
@Test fun aPastDayWithoutAYearRollsToNextYear() {
    assertEquals(LocalDate.of(2027, 1, 3), parseQuickAdd("3. 1. zavolat", now, lists).dueDate)
}
@Test fun todayWithoutAYearIsThisYear()      // "27. 11." -> 27 Nov 2026
@Test fun anImpossibleDateIsNotADate() {
    listOf("31. 2.", "32. 1.", "1. 13.").forEach { assertNull(it, parseQuickAdd(it, now, lists).dueDate) }
}
@Test fun theDotRuleKeepsADateADate() {      // "15. 3." -> 15 Mar 2027, not 15:03
@Test fun aPrepositionIsAbsorbed() {
    val r = parseQuickAdd("ve 3. 12. zavolat", now, lists)
    assertEquals(LocalDate.of(2026, 12, 3), r.dueDate)
    assertNull(r.dueMinute)
    assertEquals("zavolat", r.title)
}
@Test fun aBareNumberPairWithoutTheFinalDotIsNothing()  // "24.12 zavolat" -> no date, title unchanged
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*CalendarDate*"`. Expected: unresolved `calendarDateCandidates`.
- [ ] **Step 3: Implement `calendarDateCandidates`** and wire it into `parseQuickAdd`. Build the date with `LocalDate.of` inside a `runCatching`, so an impossible day yields no candidate rather than an exception.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: the parser understands 24. 12. and Dec 24`

---

### Task 4: Times

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/TimePatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/TimePatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's types; Task 3's family, which the dot rule is tested against.
- Produces `internal fun timeCandidates(folded: String): List<Candidate>`:
  - After `v`, `ve`, `at`, `od`, `kolem` or `okolo`, absorbed into the range: a bare hour (`ve 3`, `v 15`), an hour with minutes after a colon or a dot (`v 15:30`, `v 15.30`), an hour with `h` or `hod` (`v 15h`, `ve 3 hod`).
  - With no preposition needed, because they cannot be mistaken for anything: `15:30`, `3pm`, `7am`, `3:30 pm`.
  - **Hours 1 to 7 become afternoon** (`ve 3` → 15:00, `ve 3:30` → 15:30); 0 and 8 to 23 stand as written. A morning marker turns the rule off: `am`, `rano`, `dopoledne`, `morning` after the hour (`ve 3 rano` → 03:00, `at 3 am` → 03:00).
  - The named hours, matching bare, with an optional absorbed `v`/`ve`: `rano`/`morning` 08:00, `dopoledne` 10:00, `poledne`/`noon` 12:00, `odpoledne`/`afternoon` 15:00, `vecer`/`evening` 19:00, `noci`/`night` 22:00, and `tonight`, which sets **today's date as well as** 19:00.
- `parseQuickAdd`'s apply stage gains the no-day rule: **when a time was recognised and no date was, the date is today, or tomorrow if that minute is at or before the current minute of the day.** `dnes ve 3` at 16:30 stays on today, because a day Olda typed always wins.

- [ ] **Step 1: Write the failing tests.** `now` = Friday 27 Nov 2026 16:30 unless stated.

```kotlin
@Test fun oneToSevenReadAsAfternoon()   // "ve 3" -> 900; "at 3" -> 900; "ve 3:30" -> 930; "v 7" -> 1140
@Test fun eightToTwentyThreeStandAsWritten()  // "v 8" -> 480; "v 15" -> 900; "v 21:45" -> 1305
@Test fun aColonNeedsNoPreposition()    // "15:30 zavolat" -> 930, title "zavolat"
@Test fun theDotRuleMakesAnHourAndMinutes()  // "v 15.30" -> 930
@Test fun amAndPm()                     // "3pm" -> 900; "7am" -> 420; "at 3:30 pm" -> 930; "at 12 am" -> 0
@Test fun aMorningMarkerTurnsTheAfternoonRuleOff()  // "ve 3 rano" -> 180; "at 3 am" -> 180; "ve 3 dopoledne" -> 180
@Test fun hourSuffix()                  // "v 15h" -> 900; "ve 3 hod" -> 900
@Test fun theNamedHours()               // rano 480, dopoledne 600, v poledne 720, odpoledne 900, vecer 1140, v noci 1320
@Test fun odpoledneIsNotPoledne()       // "odpoledne" -> 900, never 720
@Test fun tonightIsTodayAtSeven() {
    val r = parseQuickAdd("tonight call Petr", now, lists)
    assertEquals(LocalDate.of(2026, 11, 27), r.dueDate); assertEquals(1140, r.dueMinute)
}
@Test fun aTimeWithNoDayThatHasGoneMeansTomorrow() {   // Olda's call, 27 Nov
    val r = parseQuickAdd("ve 3 zavolat", now, lists)  // 16:30, so 15:00 has gone
    assertEquals(LocalDate.of(2026, 11, 28), r.dueDate); assertEquals(900, r.dueMinute)
}
@Test fun aTimeWithNoDayStillToComeMeansToday()        // now 11:00, "ve 3" -> 27 Nov 15:00
@Test fun exactlyNowCountsAsGone()                     // now 15:00, "ve 3" -> 28 Nov 15:00
@Test fun aDaySaidOutLoudAlwaysWins() {
    val r = parseQuickAdd("dnes ve 3 zavolat", now, lists)
    assertEquals(LocalDate.of(2026, 11, 27), r.dueDate); assertEquals(900, r.dueMinute)
}
@Test fun aBareNumberIsNeverATime()                    // "koupit 3 rohliky" -> no time, title unchanged
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*TimePatterns*"`. Expected: unresolved `timeCandidates`.
- [ ] **Step 3: Implement `timeCandidates`**, wire it into `parseQuickAdd`, and add the no-day rule to the apply stage.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`; Task 3's `theDotRuleKeepsADateADate` still green, which is the pair that proves the dot rule works in both directions.
- [ ] **Step 5: Commit.** `feat: the parser understands ve 3, 15:30 and vecer`

---

### Task 5: Relative dates and times

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/RelativePatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/RelativePatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's types.
- Produces `internal fun relativeCandidates(folded: String, now: LocalDateTime): List<Candidate>`:
  - Days, weeks, months and years set the date only: `za 3 dny`, `za 5 dni`, `za den`, `za 2 tydny`, `za tyden`, `za mesic`, `za 3 mesice`, `za rok`; `in 3 days`, `in a day`, `in 2 weeks`, `in a week`, `in a month`, `in 3 months`, `in a year`. Czech plurals: `den|dny|dni|dnu|dnu`, `tyden|tydny|tydnu`, `mesic|mesice|mesicu`, `rok|roky|let`.
  - Hours and minutes set **both the date and the time**, counted from `now` to the minute, so they roll past midnight: `za hodinu`, `za 2 hodiny`, `za 20 minut`, `za 10 min`; `in an hour`, `in 2 hours`, `in 30 minutes`, `in 30 min`. Czech: `hodina|hodinu|hodiny|hodin`, `minuta|minutu|minuty|minut|min`.

- [ ] **Step 1: Write the failing tests.** `now` = Friday 27 Nov 2026 16:30.

```kotlin
@Test fun czechDays()        // "za 3 dny" -> 30 Nov; "za den" -> 28 Nov; "za 5 dni" -> 2 Dec
@Test fun czechWeeksMonthsYears()  // "za tyden" -> 4 Dec; "za 2 tydny" -> 11 Dec; "za mesic" -> 27 Dec; "za rok" -> 27 Nov 2027
@Test fun englishForms()     // "in 3 days", "in a day", "in a week", "in 2 weeks", "in a month", "in a year"
@Test fun hoursSetBothFields() {
    val r = parseQuickAdd("za hodinu zavolat", now, lists)
    assertEquals(LocalDate.of(2026, 11, 27), r.dueDate); assertEquals(17 * 60 + 30, r.dueMinute)
    assertEquals("zavolat", r.title)
}
@Test fun minutesSetBothFields()   // "za 20 minut" -> 27 Nov, 16:50; "in 30 min" -> 17:00
@Test fun anHourAcrossMidnightRollsTheDate() {     // now 23:30, "za hodinu" -> 28 Nov, 00:30
@Test fun manyMinutesAcrossMidnight()              // now 23:30, "za 90 minut" -> 28 Nov, 01:00
@Test fun theWholePhraseLeavesTheTitle()           // "za 3 dny koupit kabat" -> title "koupit kabat"
@Test fun aRelativeDateDoesNotAlsoSetATime()       // "za 3 dny" -> dueMinute null
@Test fun itLosesToAnAlreadyTakenField()           // "zitra za 3 dny" -> 28 Nov, "za 3 dny" left in the title
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*Relative*"`. Expected: unresolved `relativeCandidates`.
- [ ] **Step 3: Implement `relativeCandidates`** and wire it into `parseQuickAdd`. Hour and minute candidates set `date` and `minute` from one `now.plusMinutes(n)`, so the date rolls by itself.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: the parser understands za 3 dny and za hodinu`

---

### Task 6: Repeats

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/RecurrencePatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/RecurrencePatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's types; Task 2's weekday vocabulary.
- Produces `internal fun recurrenceCandidates(folded: String, today: LocalDate): List<Candidate>`. Each candidate sets `recurrence` to an RRULE fragment, **and also the `date` it implies plus the `rollOn` period of that repeat**, because a repeat with no first occurrence is something Phase 3 cannot act on:

| Folded input | `recurrence` | `date` | `rollOn` |
|---|---|---|---|
| `kazdy den`, `denne`, `every day`, `daily` | `FREQ=DAILY` | today | `P1D` |
| `kazde pondeli`, `every monday` | `FREQ=WEEKLY;BYDAY=MO` | nearest Monday, today included | `P7D` |
| `kazdy tyden`, `tydne`, `every week`, `weekly` | `FREQ=WEEKLY` | today | `P7D` |
| `kazdy mesic`, `mesicne`, `every month`, `monthly` | `FREQ=MONTHLY` | today | `P1M` |
| `kazdy rok`, `rocne`, `every year`, `yearly` | `FREQ=YEARLY` | today | `P1Y` |
| `kazde 2 dny`, `every 3 days` | `FREQ=DAILY;INTERVAL=2` | today | `P2D` |
| `kazde 2 tydny`, `every 2 weeks` | `FREQ=WEEKLY;INTERVAL=2` | today | `P14D` |

  `kazdy`, `kazda`, `kazde` and the diacritic spellings all count. `BYDAY` uses the two-letter RFC 5545 codes `MO TU WE TH FR SA SU`.
- `parseQuickAdd`'s apply stage gains the roll-forward rule: **a date that came from a repeat moves on by one `rollOn` when a time is also set and that moment has already passed.** A date Olda typed never moves, and the roll never happens without a time, because then nothing has been missed.

- [ ] **Step 1: Write the failing tests.** `now` = Friday 27 Nov 2026 16:30 unless stated.

```kotlin
@Test fun theCzechAndEnglishForms()     // the whole table above, recurrence strings exactly as written
@Test fun everyKazdyEnding()            // kazdy/kazda/kazde den all give FREQ=DAILY
@Test fun aRepeatSetsItsFirstDay() {
    assertEquals(LocalDate.of(2026, 11, 27), parseQuickAdd("kazdy den cvicit", now, lists).dueDate)
    assertEquals(LocalDate.of(2026, 11, 30), parseQuickAdd("kazde pondeli porada", now, lists).dueDate)
}
@Test fun aRepeatWithNoTimeNeverRolls() // "kazdy den cvicit" at 23:59 -> still 27 Nov
@Test fun anImpliedDayRollsOnWhenItsTimeHasGone() {   // Olda's call, after the design review
    val r = parseQuickAdd("kazdy den ve 3 cvicit", now, lists)   // 16:30
    assertEquals(LocalDate.of(2026, 11, 28), r.dueDate); assertEquals(900, r.dueMinute)
}
@Test fun anImpliedDayStaysWhenItsTimeIsStillToCome()  // now 11:00 -> 27 Nov 15:00
@Test fun aWeeklyRepeatRollsByAWeek() {
    val monday = LocalDateTime.of(2026, 11, 23, 11, 0)
    assertEquals(LocalDate.of(2026, 11, 30), parseQuickAdd("kazde pondeli v 9 porada", monday, lists).dueDate)
}
@Test fun aTypedDayIsNeverRolled() {
    val r = parseQuickAdd("dnes kazdy den ve 3 cvicit", now, lists)
    assertEquals(LocalDate.of(2026, 11, 27), r.dueDate)
}
@Test fun theWholePhraseLeavesTheTitle()  // "kazde pondeli v 9 porada" -> title "porada"
@Test fun kazdyAloneIsNotARepeat()        // "kazdy clovek" -> recurrence null, title unchanged
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*Recurrence*"`. Expected: unresolved `recurrenceCandidates`.
- [ ] **Step 3: Implement `recurrenceCandidates`** and wire it in; add the roll-forward rule to the apply stage, after the no-day rule from Task 4 and only for a date whose accepted candidate carried a `rollOn`.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: the parser understands kazdy den and kazde pondeli`

---

### Task 7: The list, the tags and the priority

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/parser/SymbolPatterns.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/parser/QuickAddParser.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/parser/SymbolPatternsTest.kt`

**Interfaces:**
- Consumes: Task 1's types.
- Produces, in `SymbolPatterns.kt`:
  - `internal fun listMatchName(name: String): String` — folds a list's stored name and then drops every character that is not a letter, a digit, a space, `_` or `-`, and trims. `"🌊Práce"` becomes `"prace"`. This exists because Olda's list names carry their emoji inside `name`, with the `icon` column unused since Phase 1.
  - `internal fun symbolCandidates(folded: String, knownLists: List<String>): List<Candidate>`:
    - `#name` matches a list whose `listMatchName` equals `name`, or failing that exactly one list whose `listMatchName` starts with `name`. The candidate's `listName` is the **stored** name, verbatim. An unknown name and an ambiguous prefix both produce **no candidate**, so the word stays in the title and the task goes to Inbox.
    - `@name` always produces a candidate with `tagName = name` as typed-and-folded. Unknown tags are created at save time, not here.
    - `p1` to `p4`, in any case, produce a `priority` candidate.
    - A `#` or `@` name is one or more of letters, digits, `_`, `-`.

- [ ] **Step 1: Write the failing tests.** `lists` = Olda's five, `now` = Friday 27 Nov 2026 16:30.

```kotlin
@Test fun anExactNameMatchesPastTheEmoji() {
    assertEquals("🌊Práce", parseQuickAdd("zavolat #prace", now, lists).listName)
    assertEquals("☀️Osobní", parseQuickAdd("zavolat #osobni", now, lists).listName)
}
@Test fun diacriticsAndCaseDoNotMatter()  // "#Práce" and "#PRACE" both match "🌊Práce"
@Test fun aUniquePrefixIsEnough()         // "#pra" -> "🌊Práce"; "#skol" -> "🎓Škola"
@Test fun anAmbiguousPrefixMatchesNothing() {
    val r = parseQuickAdd("zavolat #s", now, lists)   // Škola and Someday both start with s
    assertNull(r.listName); assertEquals("zavolat #s", r.title)
}
@Test fun anUnknownListStaysInTheTitle() {
    val r = parseQuickAdd("zavolat #xyz", now, lists)
    assertNull(r.listName); assertEquals("zavolat #xyz", r.title)
}
@Test fun aSecondListIsIgnoredAndLeftInTheTitle()  // "#prace #skola" -> Práce, title "#skola"
@Test fun tagsAreAlwaysRecognisedAndAccumulate() {
    val r = parseQuickAdd("zavolat @telefon @rychle", now, lists)
    assertEquals(listOf("telefon", "rychle"), r.tagNames); assertEquals("zavolat", r.title)
}
@Test fun anEmailIsNotATag() {
    val r = parseQuickAdd("napsat na jan@firma.cz", now, lists)
    assertEquals(emptyList<String>(), r.tagNames); assertEquals("napsat na jan@firma.cz", r.title)
}
@Test fun priorities()                    // "p1".."p4" and "P1" -> 1..4, dropped from the title
@Test fun notAPriorityInsideAWord()       // "typ1a" and "p5" -> priority null, title unchanged
@Test fun theWholeLineFromTheSpec() {
    val r = parseQuickAdd("zitra ve 3 zavolat na urad #prace", now, lists)
    assertEquals(LocalDate.of(2026, 11, 28), r.dueDate)
    assertEquals(900, r.dueMinute)
    assertEquals("🌊Práce", r.listName)
    assertEquals("zavolat na urad", r.title)
}
```

- [ ] **Step 2: Run them and watch them fail.** Run: `gradlew.bat testDebugUnitTest --tests "*Symbol*"`. Expected: unresolved `symbolCandidates`.
- [ ] **Step 3: Implement `listMatchName` and `symbolCandidates`** and wire them into `parseQuickAdd`.
- [ ] **Step 4: Run the unit tests and watch them pass.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: the parser understands #list, @tag and p1 to p4`

---

### Task 8: The phrase suite

The spec's main deliverable: 100+ real phrases in both languages, in one table where adding a phrase is adding a row. Expect it to find gaps in Tasks 2 to 7 — that is what it is for.

**Files:**
- Create: `app/src/test/java/cz/olda/tasks/data/parser/QuickAddPhraseSuiteTest.kt`
- Modify: whichever pattern family a failing row exposes

**Interfaces:**
- Consumes: everything from Tasks 1 to 7.
- Produces nothing the app uses.

- [ ] **Step 1: Write the suite.** One row per phrase, read against a fixed `now` of Friday 27 November 2026, 16:30, and Olda's five list names:

```kotlin
private data class Phrase(
    val input: String,
    val date: LocalDate? = null,
    val minute: Int? = null,
    val list: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Int? = null,
    val recurrence: String? = null,
    val title: String,
)

@Test fun everyPhraseParses() {
    val failures = phrases.mapNotNull { p ->
        val got = parseQuickAdd(p.input, now, lists)
        val want = ParsedInput(p.title, p.date, p.minute, p.list, p.tags, p.priority, p.recurrence, got.tokens)
        if (got == want) null else "${p.input}\n  want $want\n  got  $got"
    }
    assertEquals("", failures.joinToString("\n"))
}

@Test fun theSuiteIsBigEnoughAndCoversBothLanguages() {
    assertTrue("only ${phrases.size} phrases", phrases.size >= 100)
    assertTrue(phrases.count { it.recurrence != null } >= 8)
    assertTrue(phrases.count { it.minute != null } >= 25)
    assertTrue(phrases.count { it.tags.isNotEmpty() } >= 8)
}
```

  Required coverage, with Olda's own vocabulary from Appendix A of the spec (`zavolat na urad`, `koupit zimni kabat`, `napsat na ekonomku`, `porada`, `cvicit`, `odevzdat dokumentaci`):
  - every row of the two phrase tables in `docs/design.md` and of the Dates, Times, Repeats and Symbols tables in the spec, in both languages;
  - each Czech phrase **twice, once with diacritics and once without** (`zítra` and `zitra`);
  - at least 10 lines mixing the languages (`tomorrow ve 3 zavolat #prace`, `zitra at 3 pm call Petr`);
  - at least 10 lines combining four or more fields (`pristi patek v 9:30 odevzdat dokumentaci #skola @urgent p1`);
  - the tokens in different places in the line: first, last, in the middle;
  - the spec's own line, `zitra ve 3 zavolat na urad #prace`;
  - at least 10 lines where something is deliberately **not** recognised and must survive in the title (`koupit 3 rohliky`, `pondeli resetovat`, `#xyz`, `24.12`, `jan@firma.cz`, `p5`).
- [ ] **Step 2: Run it and watch it fail.** Run: `gradlew.bat testDebugUnitTest --tests "*PhraseSuite*"`. Expected: a list of the rows no pattern yet handles.
- [ ] **Step 3: Fix the pattern families** the failures point at, one family at a time, until the suite is green. A row that turns out to be a phrase the spec deliberately left out (`v pul ctvrte`, `24/12`) is moved to the not-recognised group with a comment, not deleted.
- [ ] **Step 4: Run the whole unit suite.** Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`, with the negative cases from Task 1 and every earlier family still green.
- [ ] **Step 5: Commit.** `test: 100+ Czech and English quick-add phrases`

---

### Task 9: The recurrence column

The only change to Olda's live database in this phase.

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/data/entity/TaskEntity.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/Migrations.kt`
- Modify: `app/src/main/java/cz/olda/tasks/AppContainer.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/dao/TaskDao.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/TaskRepository.kt`
- Create: `app/src/androidTest/java/cz/olda/tasks/data/MigrationTest.kt`
- Modify: `app/src/androidTest/java/cz/olda/tasks/data/TaskRepositoryTest.kt`
- Commit: `app/schemas/cz.olda.tasks.data.AppDatabase/2.json` (generated by the build)

**Interfaces:**
- Produces:
  - `TaskEntity` gains `val recurrence: String? = null` — an RRULE fragment; Phase 3 acts on it.
  - `AppDatabase` at `version = 2`.
  - `val MIGRATION_1_2: Migration` in `data/Migrations.kt`, running `ALTER TABLE tasks ADD COLUMN recurrence TEXT DEFAULT NULL`. `androidx.room.migration.Migration` and `androidx.sqlite.db.SupportSQLiteDatabase` are not `android.*`, so `data/` stays platform-free.
  - `AppContainer` passes `.addMigrations(MIGRATION_1_2)` to the one `Room.databaseBuilder` call. **No `fallbackToDestructiveMigration`, ever.**
  - `TaskDao.updateEditableAndFollow` gains a `recurrence: String?` parameter and writes that column, so a repeat can be cleared later.
  - `TaskRepository.add(...)` gains `recurrence: String? = null`; `TaskRepository.update(task)` passes `task.recurrence` through.

- [ ] **Step 1: Back up the phone's database.** Run the three `adb exec-out` commands from Global Constraints and check the sizes (94208 / 32768 / 16512).
- [ ] **Step 2: Write the failing migration test** in `MigrationTest.kt`, using `MigrationTestHelper` against the committed `schemas/`:

```kotlin
@get:Rule val helper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
)

@Test fun aTaskWrittenAtVersionOneSurvivesWithNoRepeat() {
    helper.createDatabase(DB, 1).use { db ->
        db.execSQL("INSERT INTO lists (name, colorHex, position, isInbox, updatedAt) VALUES ('Inbox', '#3D6BE0', 0, 1, 0)")
        db.execSQL(
            "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, priority, done, position, updatedAt) " +
                "VALUES ('Koupit zimní kabát', '', 'koupit zimni kabat', '', 1, 4, 0, 0, 0)",
        )
    }
    helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
        db.query("SELECT title, dueDate, priority, recurrence FROM tasks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Koupit zimní kabát", c.getString(0))
            assertTrue(c.isNull(1)); assertEquals(4, c.getInt(2)); assertTrue(c.isNull(3))
        }
    }
}
```

  And in `TaskRepositoryTest.kt`:

```kotlin
@Test fun addStoresARepeatAndUpdateCanClearIt()   // add(recurrence = "FREQ=DAILY"), read it back,
                                                   // update(task.copy(recurrence = null)), read null
```

- [ ] **Step 3: Wake the phone and run them, and watch them fail.** Run: `adb shell input keyevent KEYCODE_WAKEUP` then `gradlew.bat connectedDebugAndroidTest --tests "*MigrationTest*"`. Expected: compile error, unresolved `MIGRATION_1_2` and `recurrence`.
- [ ] **Step 4: Restore the backup.** The run above uninstalled the app.
- [ ] **Step 5: Implement the column, the migration and the repository changes** with the signatures above. Let the build regenerate `schemas/2.json`.
- [ ] **Step 6: Run the unit tests**, which must still pass unchanged. Run: `gradlew.bat testDebugUnitTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 7: Back up, wake the phone, run the device tests, restore.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`, every Phase 1 device test included.
- [ ] **Step 8: Prove the real database migrates.** Run `gradlew.bat installDebug`, open the app on the phone, and confirm all 31 tasks are there and Today looks right. This is the step the backup exists for.
- [ ] **Step 9: Commit.** `feat: a recurrence column, with a migration from version 1`

---

### Task 10: Tags

`tags` and `task_tags` have existed since Phase 1 and nothing has ever used them.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/dao/TagDao.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/TagRepository.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt` (the DAO getter)
- Modify: `app/src/main/java/cz/olda/tasks/AppContainer.kt` (expose `tagRepository`)
- Create: `app/src/androidTest/java/cz/olda/tasks/data/TagRepositoryTest.kt`

**Interfaces:**
- Consumes: `TagEntity`, `TaskTagCrossRef`, Task 7's `listMatchName` is **not** used here; tags match on `fold` alone.
- Produces `class TagRepository(tagDao: TagDao)`:
  - `fun observeTagsOf(taskId: Long): Flow<List<TagEntity>>` — a task's tags, by name.
  - `fun observeTags(): Flow<List<TagEntity>>` — every tag, by name.
  - `suspend fun attach(taskId: Long, names: List<String>)` — for each name, finds the tag whose folded name matches or creates one with the Slate preset's hex from `ListPalette`/`docs/design.md`, then attaches it. **Duplicate names in one call attach once**, and attaching a tag a task already has changes nothing: `task_tags` has `primaryKeys = ["taskId", "tagId"]`, so the insert must be `OnConflictStrategy.IGNORE`. This is Review Focus 1.
  - `suspend fun detach(taskId: Long, tagId: Long)`
  - `tags` has no unique index on `name` and adding one would be a second schema change, so the find-or-create de-duplicates here; the quick-add save already runs under a mutex, so there is one writer at a time.

- [ ] **Step 1: Write the failing tests** in `TagRepositoryTest.kt`, against an in-memory database, in the style of `ListRepositoryTest`:

```kotlin
@Test fun attachCreatesATagThatDidNotExist()        // one tag named "telefon", Slate hex, attached
@Test fun attachReusesATagIgnoringCaseAndDiacritics()  // "Telefon" then "telefon" -> one row in tags
@Test fun theSameNameTwiceInOneCallAttachesOnce()   // Review Focus 1: attach(id, listOf("telefon","telefon"))
@Test fun attachingATagTheTaskAlreadyHasIsHarmless()   // attach twice -> one cross-ref, no exception
@Test fun observeTagsOfReturnsOnlyThatTasksTags()
@Test fun detachRemovesTheLinkAndKeepsTheTag()
```

- [ ] **Step 2: Back up, wake the phone, run them, watch them fail, restore.** Run: `gradlew.bat connectedDebugAndroidTest --tests "*TagRepository*"`. Expected: unresolved `TagRepository`.
- [ ] **Step 3: Implement `TagDao`, `TagRepository`**, the `AppDatabase` getter and the `AppContainer` property.
- [ ] **Step 4: Back up, wake, run the device tests, restore.** Run: `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: tags can be created, attached and removed`

---

### Task 11: The quick-add sheet understands what you type

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddViewModel.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt` — `setTitle` becomes `setInput` and `draftTitle` becomes `draftInput`, and the text field shows `state.input`, not `state.title`; the sheet does not compile until it follows
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`
- Modify: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt`
- Create: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddViewModelTest.kt`

**Interfaces:**
- Consumes: `parseQuickAdd` (Tasks 1–7), `TagRepository.attach` (Task 10), `TaskRepository.add(recurrence = …)` (Task 9), `AppContainer.clock`.
- Produces:
  - `QuickAddViewModel(taskRepository, listRepository, tagRepository, clock, savedState)` — the new `tagRepository` and `clock` are wired in `AppViewModelProvider` from `AppContainer`.
  - `QuickAddState` gains `val input: String` (the raw line in the field), `val recurrence: String?`, `val tagNames: List<String>`, and keeps `title` as the cut title that will be saved. `dueMinute` stops being hard-coded null.
  - `fun setInput(text: String)` replaces `setTitle`: it stores the raw line, parses it with `LocalDateTime.now(clock)` and the current list names, and derives the rest of the state. `draftTitle` becomes `draftInput`.
  - `setDate`, `setMinute`, `setList`, `setPriority`, `clearRecurrence` each **pin** their field: a pinned field keeps the value Olda chose and is no longer touched by the parser. Pins live in `savedState` beside the draft. An empty input clears every pin.
  - **A pin changes the value, never the cut:** the title is always the parsed title, whatever the chips say.
  - `save()` writes the cut title, the date, the minute, the priority, the recurrence and the list, then calls `TagRepository.attach` with the parsed tag names. A parsed list that no longer exists falls back to Inbox, exactly as a chosen one already does (Review Focus 5).

- [ ] **Step 1: Write the failing tests** in `QuickAddViewModelTest.kt`, against an in-memory database seeded with Olda's five lists:

```kotlin
@Test fun typingFillsTheFieldsAndCutsTheTitle()   // "zitra ve 3 zavolat na urad #prace" ->
    // dueDate tomorrow, dueMinute 900, listId of Práce, title "zavolat na urad", input unchanged
@Test fun savingWritesEverythingTheParserFound()  // the task row has the date, minute, priority, recurrence
@Test fun savingAttachesTheParsedTags()           // "zavolat @telefon @rychle" -> two tags on the task
@Test fun aPinnedDateSurvivesFurtherTyping() {
    vm.setInput("zitra zavolat"); vm.setDate(LocalDate.of(2026, 12, 4))
    vm.setInput("zitra zavolat Petrovi")
    assertEquals(LocalDate.of(2026, 12, 4), vm.state.value.dueDate)
    assertEquals("zavolat Petrovi", vm.state.value.title)   // the cut still happens
}
@Test fun clearingTheFieldUnpinsEverything()
@Test fun aLineOfNothingButTokensCannotBeSaved() {           // Review Focus 2
    vm.setInput("zitra #prace p1")
    assertEquals("", vm.state.value.title); assertFalse(vm.state.value.canSave)
    assertEquals(LocalDate.of(2026, 11, 28), vm.state.value.dueDate)   // the chips still say what it understood
}
@Test fun aParsedListThatIsDeletedBeforeSavingFallsBackToInbox()   // Review Focus 5
@Test fun anUnknownListLeavesTheWordAndGoesToInbox()
```

  And in `QuickAddSheetTest.kt`, one test through the UI with `performTextInput`, which sets the field directly and so does not depend on the phone's keyboard:

```kotlin
@Test fun typingALineShowsItOnTheChips()   // type the spec's line, assert the date and list chips' text
```

- [ ] **Step 2: Back up, wake the phone, run them, watch them fail, restore.** Run: `gradlew.bat connectedDebugAndroidTest --tests "*QuickAdd*"`. Expected: unresolved `setInput`, `tagRepository`.
- [ ] **Step 3: Implement the view-model changes** with the signatures above, and update `AppViewModelProvider`.
- [ ] **Step 4: Back up, wake, run both suites, restore.** Run: `gradlew.bat testDebugUnitTest` and `gradlew.bat connectedDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: quick-add parses the line as you type it`

---

### Task 12: The chips

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddChips.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt`

**Interfaces:**
- Consumes: Task 11's `QuickAddState`; `formatMinuteOfDay` from `ui/components/TaskRow.kt`; the time-picker dialog pattern in `TaskDetailScreen.kt:289-315`; the springs in `ui/theme/Motion.kt`.
- Produces: `QuickAddChips` gains
  - a **time chip** showing `formatMinuteOfDay(dueMinute)` or `R.string.no_time`, opening the same time picker task detail uses (extract it to `ui/components/` if it comes out identical), with test tag `chip-time`;
  - a **repeat chip**, shown only when `recurrence != null`, labelled `↺ ` plus the words Olda typed — his own spelling, data rather than interface text — tapping it clears the repeat, with test tag `chip-repeat`;
  - **tag chips**, one per parsed tag name, labelled `@name`, with test tags `chip-tag-<name>`;
  - a **pop** when a chip's value changes, from the existing spring vocabulary, which with Android's "remove animations" on simply becomes a change.
- New strings: none beyond what exists, except a content description for the repeat chip (`quick_add_clear_repeat`). `no_time`, `no_date` and `chip_priority_value` are already there.

- [ ] **Step 1: Write the failing tests** in `QuickAddSheetTest.kt`:

```kotlin
@Test fun theTimeChipShowsAParsedTime()        // type "zitra ve 3 zavolat" -> chip-time reads "15:00"
@Test fun theTimeChipOpensThePickerAndPinsWhatYouChoose()
@Test fun theRepeatChipAppearsOnlyWhenThereIsARepeat()   // absent for "zavolat", present for "kazdy den cvicit"
@Test fun theRepeatChipEchoesWhatWasTyped()    // "kazdy den cvicit" -> chip-repeat contains "kazdy den"
@Test fun tappingTheRepeatChipClearsIt()
@Test fun aTagChipAppearsPerTag()              // "zavolat @telefon @rychle" -> chip-tag-telefon, chip-tag-rychle
```

- [ ] **Step 2: Back up, wake the phone, run them, watch them fail, restore.** Expected: no node with tag `chip-time`.
- [ ] **Step 3: Implement the chips** and the strings.
- [ ] **Step 4: Back up, wake, run both suites, restore.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: quick-add chips for the time, the repeat and the tags`

---

### Task 13: Task detail shows tags and can clear a repeat

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/task/TaskDetailScreen.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/task/TaskDetailViewModel.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/task/RecurrenceLabel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/cz/olda/tasks/ui/task/TaskDetailScreenTest.kt`
- Test: `app/src/test/java/cz/olda/tasks/ui/task/RecurrenceLabelTest.kt`

**Interfaces:**
- Consumes: `TagRepository` (Task 10), `TaskRepository.update` with `recurrence` (Task 9).
- Produces:
  - In `RecurrenceLabel.kt`: `data class RecurrenceLabel(val stringId: Int, val interval: Int? = null, val day: DayOfWeek? = null)` and `fun recurrenceLabel(rule: String): RecurrenceLabel` — turns a stored rule into a string resource plus at most one argument, because by the time a task is open Olda's own words are gone and there is no `values-cs`. It returns a resource id and plain values rather than reading resources, so it is testable on the JVM; the composable formats `day` with `getDisplayName(TextStyle.FULL, Locale.getDefault())`, the way `WeekStrip.kt:76` already names a weekday, so this phase adds no weekday strings.
  - New strings: `repeat_daily` "Every day", `repeat_weekly` "Every week", `repeat_monthly` "Every month", `repeat_yearly` "Every year", `repeat_weekly_on` "Every %1$s", `repeat_every_n_days` "Every %1$d days", `repeat_every_n_weeks` "Every %1$d weeks", `repeat_unknown` "Repeats", `repeat` "Repeat", `remove_repeat` "Remove the repeat", `tags` "Tags", `add_tag` "Add a tag", `remove_tag` "Remove %1$s".
  - `TaskDetailViewModel` gains `tagRepository`, exposes the task's tags, and `fun addTag(name: String)`, `fun removeTag(tagId: Long)`, `fun clearRecurrence()`.
  - Task detail gains a **tag row** of chips with an × each and a `+ tag` chip opening a small dialog with a text field, and a **repeat row** shown only when the task has one, with an × that clears it. **Changing** a repeat waits for Phase 3's picker.
  - The title field on this screen stays literal: nothing here is parsed.

- [ ] **Step 1: Write the failing tests.** In `RecurrenceLabelTest.kt`:

```kotlin
@Test fun eachRuleGetsItsOwnLabel() {
    assertEquals(RecurrenceLabel(R.string.repeat_daily), recurrenceLabel("FREQ=DAILY"))
    assertEquals(RecurrenceLabel(R.string.repeat_weekly_on, day = DayOfWeek.MONDAY), recurrenceLabel("FREQ=WEEKLY;BYDAY=MO"))
    assertEquals(RecurrenceLabel(R.string.repeat_every_n_days, interval = 3), recurrenceLabel("FREQ=DAILY;INTERVAL=3"))
    // and FREQ=WEEKLY, FREQ=MONTHLY, FREQ=YEARLY, FREQ=WEEKLY;INTERVAL=2
}
@Test fun anUnreadableRuleFallsBackToRepeats() {
    listOf("", "nonsense", "FREQ=HOURLY").forEach { assertEquals(RecurrenceLabel(R.string.repeat_unknown), recurrenceLabel(it)) }
}
```

  In `TaskDetailScreenTest.kt`:

```kotlin
@Test fun aTasksTagsAreShown()
@Test fun aTagCanBeAddedAndRemoved()
@Test fun theRepeatRowAppearsOnlyForARepeatingTask()
@Test fun theRepeatCanBeCleared()          // tap ×, the row goes, the stored task has recurrence null
@Test fun theTitleFieldIsNotParsed() {     // type "Číst při Týdenním resetu (pondělí)", save, nothing re-dated
```

- [ ] **Step 2: Back up, wake, run them, watch them fail, restore.** Run: `gradlew.bat testDebugUnitTest --tests "*RecurrenceLabel*"` and `gradlew.bat connectedDebugAndroidTest --tests "*TaskDetail*"`.
- [ ] **Step 3: Implement the label, the view-model additions, the two rows and the strings.**
- [ ] **Step 4: Back up, wake, run both suites, restore.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit.** `feat: task detail shows tags and can clear a repeat`

---

### Task 14: On the phone, and Olda's own phrases

**Files:**
- Modify: `app/src/test/java/cz/olda/tasks/data/parser/QuickAddPhraseSuiteTest.kt`
- Modify: `docs/superpowers/plans/2026-11-27-phase-2-quick-add-parser.md` (tick the boxes)

- [ ] **Step 1: Run everything.** Run: `gradlew.bat testDebugUnitTest`, then back up, wake the phone, `gradlew.bat connectedDebugAndroidTest`, restore. Expected: `BUILD SUCCESSFUL` for both, `DataLayerIsPlatformFreeTest` green, which is what keeps `data/parser/` ready for Phase 8.
- [ ] **Step 2: Install and check the phase's own done-when.** Run: `gradlew.bat installDebug`, then on the phone type `zitra ve 3 zavolat na urad #prace` into quick-add and save. Expected: a task tomorrow at 15:00 in 🌊Práce, titled `zavolat na urad`, and his 31 tasks all still there.
- [ ] **Step 3: Ask Olda to read the phrase list** in `QuickAddPhraseSuiteTest.kt` and send the lines he would type that are missing, plus anything the app got wrong on his phone during the day.
- [ ] **Step 4: Add his phrases as rows** and fix what they expose, one pattern family at a time.
- [ ] **Step 5: Run everything again** and commit. `test: Olda's own quick-add phrases`
- [ ] **Step 6: Push and tell Olda what to check** — in plain language, what changed and what to try on his phone.
