# Phase 3 · Reminders and recurring tasks — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A notification arrives at a task's due minute with working Done and Snooze; ticking a repeating task creates the next occurrence on the right day; and one chip hides repeats on Today and Upcoming without losing them.

**Architecture:** Two pure functions in `data/` decide everything about time — `nextOccurrence` (which day a repeat lands on next) and `ringAt`/`nextRing` (which minute a task rings at, and which ring is next) — so both are tested on the PC in seconds. A `Flow` over the tasks table recomputes the next ring on every write, and one Android-only file books a single exact alarm for it; when the alarm fires, a receiver posts a notification per task in that ring and books the one after. Completing a repeat is one DAO transaction that marks the task done and inserts its successor. "Hide repeats" is a boolean in a new `settings` table that the screens pass into the repository, which moves repeats aside rather than dropping them, so the folded row can count them and a tap can show them.

**Tech Stack:** Kotlin, `java.time`, Jetpack Compose, Material 3, Room (with KSP), `AlarmManager`, `NotificationManagerCompat`, JUnit 4, Compose UI tests, `room-testing`. **No new libraries.**

**Spec:** `docs/superpowers/specs/2026-11-28-phase-3-reminders-and-repeats-design.md` (which argues from `docs/design.md`, the Phase 3 roadmap row and the sections "Reminders" and "Recurring tasks")

## Global Constraints

- Package `cz.olda.tasks`, minimum SDK 29, target SDK 37, one `app` module, Gradle Kotlin DSL.
- **No new libraries.** Four new permissions and only these four: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `USE_EXACT_ALARM`, and `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"`. Still **no `INTERNET` permission**.
- **Ready for Windows (Phase 8):** everything in `data/` — the new `data/recurrence/` and `data/reminder/` included — uses no `android.*` imports and no `Context`. `DataLayerIsPlatformFreeTest` enforces it on every unit-test run. `java.time` and `androidx.room` are fine. Every Android-only file of this phase lives in the new `cz.olda.tasks.platform` package.
- **New DAOs are `abstract class`, never `interface`.** `FakeTaskDao` in `app/src/test/` subclasses `TaskDao()` so the JVM ViewModel tests can serve queries without a database; a new DAO that cannot be subclassed cannot be faked the same way.
- Code identifiers in English. Every user-facing string in `res/values/strings.xml`. There is no `values-cs`; the interface is English. The quick-add repeat chip echoing Olda's own typed words is data, not interface text.
- **The database holds Olda's 31 real tasks and there is no destructive-migration fallback, ever.** `connectedDebugAndroidTest` uninstalls the app and takes them with it. Before every device-test run: back up with `adb exec-out "run-as cz.olda.tasks cat /data/data/cz.olda.tasks/databases/tasks.db"` (and `-shm`, `-wal`) into the gitignored `.superpowers/sdd/db-backup/`, never `adb shell cat`; restore afterwards and check the sizes.
- **The phone must be awake and not in his hands**, or every Compose test fails at once with "No compose hierarchies found in the app": `adb shell input keyevent KEYCODE_WAKEUP` before each run. A failure count that changes between two identical runs means he is using the phone — `adb exec-out screencap -p` before blaming the code.
- Device: `ANDROID_SERIAL="adb-FQAUKJPJVOKZVW8H-2NpXZJ._adb-tls-connect._tcp"`. `adb` lives at `~/AppData/Local/Android/Sdk/platform-tools/adb.exe` and is not on PATH. Export `MSYS_NO_PATHCONV=1` before any adb command carrying an absolute device path. **The phone was unreachable on 28 Nov** — wireless debugging needs reconnecting in Android Studio before Task 1's device run.
- Commands (Windows: `gradlew.bat`):
  - unit tests: `gradlew.bat testDebugUnitTest`, one class with `--tests "cz.olda.tasks.X"`
  - device tests: `gradlew.bat connectedDebugAndroidTest`; **one class** with `-Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.X` (15 seconds instead of four minutes — `--tests` is not a valid option here). One full run at the end.
  - install: `gradlew.bat installDebug`
- Commit at the end of every task with a clear message, then push. Never commit `local.properties`, keystores or API keys.

## Review Focus

Five things the spec implies, that a reasonable person would expect to work, and that no task's own tests would otherwise reach. Each has its test in the task that owns the code.

1. **Several tasks ringing in the same minute.** Six dated tasks with no time all ring at 08:00. One notification must be posted for each, and the re-arm afterwards must not drop the ones that shared the instant. → Tasks 4 and 7.
2. **A repeat the app cannot act on.** `recurrence` holding `FREQ=HOURLY`, an empty string, or a rule with a nonsense interval, and a repeat on a task with no due date at all. Ticking it must still complete the task, and must never create a task on a guessed day. → Tasks 2 and 3.
3. **A ring that belongs to a task that is no longer there.** Completing, deleting or snoozing a task must re-book or cancel the alarm, and a done or soft-deleted task must never ring. → Task 5.
4. **A day whose only tasks are hidden repeats.** "Nothing due today" must not appear above a row saying "2 repeats hidden", and the glance line's "of 7" must not shrink while the done count behind its "2" stays put. → Task 15.
5. **A reminder whose minute has already passed when the task is created.** Dating a task today with no time, at 10:00, must not fire an 08:00 notification immediately, and must not fire one on every subsequent edit. → Task 4.

---

### Task 1: The migration, the snooze column and the settings store

Database version 2 → 3. One additive column on `tasks` and one new two-column table, plus the repository that reads it. Everything later in this phase needs one or the other.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/entity/SettingEntity.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/dao/SettingDao.kt`
- Create: `app/src/main/java/cz/olda/tasks/data/SettingsRepository.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/entity/TaskEntity.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/AppDatabase.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/Migrations.kt`
- Modify: `app/src/main/java/cz/olda/tasks/AppContainer.kt`
- Create: `app/schemas/cz.olda.tasks.data.AppDatabase/3.json` (written by KSP; committed)
- Test: `app/src/androidTest/java/cz/olda/tasks/data/MigrationTest.kt` (one new case)
- Test: `app/src/androidTest/java/cz/olda/tasks/data/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `TaskEntity.snoozedUntil: Long? = null` — epoch millis; the moment a snoozed reminder should ring. Documented as written only by the snooze path, never by an ordinary edit.
  - `@Entity(tableName = "settings") data class SettingEntity(@PrimaryKey val key: String, val value: String)`
  - `abstract class SettingDao` with `fun observe(key: String): Flow<String?>`, `suspend fun get(key: String): String?`, and `@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(setting: SettingEntity)`
  - `class SettingsRepository(private val settingDao: SettingDao)` with `fun observeBoolean(key: String, default: Boolean): Flow<Boolean>`, `suspend fun setBoolean(key: String, value: Boolean)`, and `companion object { const val HIDE_REPEATS = "hideRepeats"; const val NOTIFICATIONS_ASKED = "notificationsAsked" }`
  - `val MIGRATION_2_3: Migration` in `Migrations.kt`
  - `AppContainer.settingsRepository: SettingsRepository`
  - `AppDatabase.settingDao(): SettingDao`, version 3

- [ ] **Step 1: Write the failing migration test.**

In `MigrationTest.kt`, beside the existing case:

```kotlin
@Test fun aTaskWrittenAtVersionTwoSurvivesWithNoSnooze() {
    helper.createDatabase(DB, 2).use { db ->
        db.execSQL("INSERT INTO lists (name, colorHex, position, isInbox, updatedAt) VALUES ('🌊Práce', '#3D6BE0', 0, 1, 0)")
        db.execSQL(
            "INSERT INTO tasks (title, notes, titleNormalised, notesNormalised, listId, priority, " +
                "done, position, dueDate, dueMinute, recurrence, updatedAt) " +
                "VALUES ('Číst při Týdenním resetu (pondělí).', '', 'cist pri tydennim resetu (pondeli).', '', 1, 2, 0, 0, 20785, 900, 'FREQ=WEEKLY;BYDAY=MO', 7)",
        )
    }
    helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { db ->
        db.query("SELECT title, dueDate, dueMinute, recurrence, snoozedUntil FROM tasks").use { row ->
            assertTrue("the task is gone", row.moveToFirst())
            assertEquals("Číst při Týdenním resetu (pondělí).", row.getString(0))
            assertEquals(20785L, row.getLong(1))
            assertEquals(900, row.getInt(2))
            assertEquals("FREQ=WEEKLY;BYDAY=MO", row.getString(3))
            assertTrue("a migrated task must have no snooze", row.isNull(4))
            assertEquals("exactly one task", 1, row.count)
        }
        db.query("SELECT COUNT(*) FROM settings").use { row ->
            assertTrue(row.moveToFirst())
            assertEquals("the settings table starts empty", 0, row.getInt(0))
        }
    }
}
```

- [ ] **Step 2: Run it and watch it fail.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.data.MigrationTest`
Expected: FAIL — `MIGRATION_2_3` does not exist (a compile error until Step 3).
**Back up the database before this run and restore it afterwards** (Global Constraints).

- [ ] **Step 3: Add the column, the entity, the DAO and the version bump.**

`snoozedUntil: Long? = null` on `TaskEntity` after `recurrence`, with a KDoc saying what writes it. `SettingEntity` and `SettingDao` as in Interfaces. `AppDatabase`: add `SettingEntity::class` to `entities`, `version = 3`, `abstract fun settingDao(): SettingDao`.

- [ ] **Step 4: Build once so KSP exports the schema, then write the migration from it.**

Run: `gradlew.bat assembleDebug`, which writes `app/schemas/cz.olda.tasks.data.AppDatabase/3.json`.
Then write `MIGRATION_2_3` in `Migrations.kt` with two statements: `ALTER TABLE tasks ADD COLUMN snoozedUntil INTEGER DEFAULT NULL`, and the `CREATE TABLE` for `settings` **copied verbatim from the `createSql` of the `settings` entity in `3.json`** (Room validates the schema it finds against the one it generated, and a hand-written variant fails at open time with an error that does not explain itself). Register it in `AppContainer`: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 5: Run the migration test and watch it pass.**

Run: as Step 2. Expected: PASS.

- [ ] **Step 6: Write the failing settings test.**

In `SettingsRepositoryTest.kt`, an in-memory database as `TaskRepositoryTest` opens one:

```kotlin
@Test fun anAbsentFlagReadsAsItsDefault() = runBlocking {
    assertFalse(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
    assertTrue(repo.observeBoolean("whatever", default = true).first())
}
@Test fun aFlagIsRememberedAndCanBeChangedBack() = runBlocking {
    repo.setBoolean(SettingsRepository.HIDE_REPEATS, true)
    assertTrue(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
    repo.setBoolean(SettingsRepository.HIDE_REPEATS, false)
    assertFalse(repo.observeBoolean(SettingsRepository.HIDE_REPEATS, default = false).first())
}
@Test fun twoFlagsDoNotCollide() = runBlocking {
    repo.setBoolean(SettingsRepository.HIDE_REPEATS, true)
    assertFalse(repo.observeBoolean(SettingsRepository.NOTIFICATIONS_ASKED, default = false).first())
}
```

- [ ] **Step 7: Run it and watch it fail.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.data.SettingsRepositoryTest`
Expected: FAIL — `SettingsRepository` does not exist.

- [ ] **Step 8: Implement `SettingsRepository`.**

`observeBoolean` maps the stored string through `toBooleanStrictOrNull() ?: default`, so a value written by a later phase cannot crash a screen. `setBoolean` writes `"true"` / `"false"`. Expose `settingsRepository` from `AppContainer`.

- [ ] **Step 9: Run both device classes and the whole unit suite.**

Run: the two commands above, then `gradlew.bat testDebugUnitTest`.
Expected: PASS, and `DataLayerIsPlatformFreeTest` still green (`data/` gained no `android.*`).

- [ ] **Step 10: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data app/src/main/java/cz/olda/tasks/AppContainer.kt app/schemas app/src/androidTest/java/cz/olda/tasks/data
git commit -m "feat: database 3 adds a snooze column and a settings table"
```

---

### Task 2: Which day the next occurrence falls on

The pure half of repeats, and the one place the "keep the schedule, skip the misses" rule lives. No Android, no database, no clock.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/recurrence/NextOccurrence.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/recurrence/NextOccurrenceTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `fun nextOccurrence(rule: String, dueDate: LocalDate?, today: LocalDate): LocalDate?` — the first date in `rule`'s series strictly after `today`, anchored on `dueDate` (or on `today` when it is null). Null when the rule cannot be read, when its interval is not a positive number, or when the series needs more than 1,000 steps to pass `today`.

- [ ] **Step 1: Write the failing tests.**

One per row of the spec's table, plus the refusals. `dueDate` and `today` are `LocalDate.of(...)`; December 2026 begins on a Tuesday, so Monday 1 Dec below is 2026-11-30 — use explicit dates and name them by weekday in the test name only.

```kotlin
private val mon = LocalDate.of(2026, 11, 30)   // Monday
private val wed = LocalDate.of(2026, 12, 2)    // Wednesday
private val fri = LocalDate.of(2026, 12, 4)    // Friday

@Test fun dailyTickedLateSkipsTheMissedDays() =
    assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", mon, wed))
@Test fun dailyTickedOnTimeStepsOnce() =
    assertEquals(LocalDate.of(2026, 12, 1), nextOccurrence("FREQ=DAILY", mon, mon))
@Test fun dailyTickedEarlyStillStepsFromItsDueDate() =
    assertEquals(LocalDate.of(2026, 12, 5), nextOccurrence("FREQ=DAILY", fri, wed))
@Test fun anIntervalKeepsItsRhythmRatherThanRestarting() =
    assertEquals(LocalDate.of(2026, 12, 6), nextOccurrence("FREQ=DAILY;INTERVAL=3", mon, fri))
@Test fun weeklyStepsSevenDays() =
    assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY", mon, wed))
@Test fun everyTwoWeeksKeepsItsParity() =
    assertEquals(LocalDate.of(2026, 12, 14), nextOccurrence("FREQ=WEEKLY;INTERVAL=2", mon, wed))
@Test fun aBydayRuleStaysOnItsWeekday() =
    assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY;BYDAY=MO", mon, wed))
@Test fun aBydayRuleMovesOffADueDateThatIsTheWrongWeekday() =
    assertEquals(LocalDate.of(2026, 12, 7), nextOccurrence("FREQ=WEEKLY;BYDAY=MO", wed, wed))
@Test fun monthlyClampsToAShortMonth() =
    assertEquals(LocalDate.of(2027, 2, 28), nextOccurrence("FREQ=MONTHLY", LocalDate.of(2027, 1, 31), LocalDate.of(2027, 1, 31)))
@Test fun everyThreeMonthsIsQuarterly() =
    assertEquals(LocalDate.of(2027, 6, 1), nextOccurrence("FREQ=MONTHLY;INTERVAL=3", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1)))
@Test fun yearlyClampsALeapDay() =
    assertEquals(LocalDate.of(2029, 2, 28), nextOccurrence("FREQ=YEARLY", LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29)))
@Test fun everyTwoYearsSkipsAYear() =
    assertEquals(LocalDate.of(2028, 6, 1), nextOccurrence("FREQ=YEARLY;INTERVAL=2", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)))
@Test fun withNoDueDateTodayIsTheAnchor() =
    assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", null, wed))
@Test fun aRuleFromAnotherEraCatchesUp() =
    assertEquals(LocalDate.of(2026, 12, 3), nextOccurrence("FREQ=DAILY", LocalDate.of(2020, 1, 1), wed))

// Refusals: every one of these must be null, and none may throw.
@Test fun anUnreadableRuleIsRefused() {
    listOf("", "FREQ=HOURLY", "FREQ=", "nonsense", "FREQ=DAILY;INTERVAL=0",
           "FREQ=DAILY;INTERVAL=-2", "FREQ=DAILY;INTERVAL=x", "FREQ=WEEKLY;BYDAY=XX",
           "FREQ=DAILY;FREQ=WEEKLY")
        .forEach { assertNull(it, nextOccurrence(it, mon, wed)) }
}
@Test fun aRuleOlderThanTheStepCapIsRefused() =
    assertNull(nextOccurrence("FREQ=YEARLY", LocalDate.of(1970, 1, 1), wed))
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `gradlew.bat testDebugUnitTest --tests "cz.olda.tasks.data.recurrence.NextOccurrenceTest"`
Expected: FAIL — unresolved reference `nextOccurrence`.

- [ ] **Step 3: Implement `nextOccurrence` in `NextOccurrence.kt`.**

Parse the rule the way `ui/task/RecurrenceLabel.kt` already does (split on `;`, then on `=`, first value per key wins is **not** acceptable — a duplicated `FREQ` is a refusal, so reject a rule with a repeated key). `BYDAY` maps through `DayOfWeek.entries.associateBy { it.name.take(2) }`, as `RecurrenceLabel.kt` does. For a `BYDAY` rule the anchor moves to the next matching weekday on or after the due date before stepping; for everything else the anchor is the due date itself. Then step `plusDays`/`plusWeeks`/`plusMonths`/`plusYears` by the interval until the date is after `today`, capped at 1,000 steps.

- [ ] **Step 4: Run them and watch them pass.**

Run: as Step 2. Expected: PASS, 16 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data/recurrence app/src/test/java/cz/olda/tasks/data/recurrence
git commit -m "feat: work out the next occurrence of a repeat"
```

---

### Task 3: Ticking a repeat creates the next one

The one door every completion already goes through learns about repeats. Everything on screen and the notification's Done button inherit it at once.

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/data/dao/TaskDao.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/TaskRepository.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/data/RecurringCompletionTest.kt`

**Interfaces:**
- Consumes: `nextOccurrence` (Task 2); `TaskEntity.snoozedUntil` (Task 1).
- Produces:
  - `TaskDao.copyTags(fromId: Long, toId: Long)` — `@Query("INSERT INTO task_tags (taskId, tagId) SELECT :toId, tagId FROM task_tags WHERE taskId = :fromId")`
  - `TaskDao.deleteHard(id: Long)` — `@Query("DELETE FROM tasks WHERE id = :id AND done = 0")`
  - `TaskDao.completeAndCreateNext(id: Long, nextDueDate: Long, now: Long): Long` — `@Transaction open suspend`; reads the task, calls the existing `setDone(id, true, now)`, inserts `task.copy(...)` with the fields the spec lists, copies the tags, returns the new id (0 when the task was gone).
  - `TaskRepository.setDone(id: Long, done: Boolean): Long?` — **signature change**, now returning the created occurrence's id or null. Existing callers ignore the result and do not change.
  - `TaskRepository.undoCompletion(id: Long)` — unchanged signature; now also hard-deletes the occurrence that the matching completion created.

- [ ] **Step 1: Write the failing tests.**

`RecurringCompletionTest.kt`, opened like `TaskRepositoryTest` (in-memory database, `Clock.fixed` at 2026-12-02 10:00 Europe/Prague, so `today` is the Wednesday above).

```kotlin
@Test fun tickingADailyTaskCreatesTomorrow()            // due today → a second open task due 2026-12-03
@Test fun tickingALateDailyTaskSkipsTheMissedDays()     // due 2026-11-30 → the new one is due 2026-12-03
@Test fun aWeeklyMondayRuleStaysOnMonday()              // FREQ=WEEKLY;BYDAY=MO due 11-30 → 12-07
@Test fun theNextOccurrenceKeepsEverythingAboutTheTask()
    // same title, notes, listId, headingId, dueMinute, priority, recurrence; done = false,
    // completedAt = null, snoozedUntil = null, googleId = null, a fresh id
@Test fun theNextOccurrenceCarriesTheTags()             // two tags attached → both on the new task
@Test fun theCompletedTaskKeepsItsRuleAndIsDone()
@Test fun theNextOccurrenceHasNoSubtasks()              // a parent with two subtasks: the new task has none,
                                                        // and the old subtasks are completed as before
@Test fun aRepeatWithNoDateStartsFromToday()            // dueDate null → the new one is due 2026-12-03
@Test fun anUnreadableRuleJustCompletesTheTask()        // recurrence = "FREQ=HOURLY" → done, and no second task
@Test fun aTaskWithNoRuleJustCompletes()                // the Phase 1 behaviour, unchanged
@Test fun reopeningATaskCreatesNothing()                // setDone(id, false) returns null and adds no task
@Test fun undoRemovesTheOccurrenceItCreated()           // complete, undoCompletion, then exactly one open task,
                                                        // reopened, and no leftover soft-deleted row
@Test fun undoOfADifferentTaskLeavesTheOccurrenceAlone()
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.data.RecurringCompletionTest`
Expected: FAIL. **Back up the database first.**

- [ ] **Step 3: Implement the DAO transaction and the repository wiring.**

`completeAndCreateNext` builds the successor with `task.copy(id = 0, dueDate = nextDueDate, done = false, completedAt = null, parentTaskId = null, snoozedUntil = null, googleId = null, position = nextPosition(task.listId, task.headingId, null), updatedAt = now)` — a copy, not a column list, so a column added in a later phase travels by default. `setDone` computes the next date only when `done` is true and `recurrence` is non-null, and falls back to the plain `setDone` when `nextOccurrence` returns null. `undoCompletion` consults a private `var lastRoll: Pair<Long, Long>?` written by `setDone`, with a KDoc saying why it is in memory and not in the database.

- [ ] **Step 4: Run them and watch them pass.**

Run: as Step 2. Expected: PASS, 13 tests.

- [ ] **Step 5: Run the existing data device tests, which the signature change touches.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.data.TaskRepositoryTest`
Expected: PASS, unchanged.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data app/src/androidTest/java/cz/olda/tasks/data
git commit -m "feat: ticking a recurring task creates the next one"
```

---

### Task 4: Which minute a task rings at

The pure half of reminders. A ring is **a moment and the list of tasks at it** — the design review's correction, and the reason six 08:00 tasks all get a notification.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/data/reminder/ReminderPlan.kt`
- Test: `app/src/test/java/cz/olda/tasks/data/reminder/ReminderPlanTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class ReminderInput(val taskId: Long, val dueDate: Long?, val dueMinute: Int?, val snoozedUntil: Long?)` — the column names Task 5's DAO query aliases to.
  - `data class Ring(val atMillis: Long, val taskIds: List<Long>)`
  - `const val MORNING_MINUTE = 8 * 60`
  - `fun ringAt(task: ReminderInput, zone: ZoneId, morningMinute: Int = MORNING_MINUTE): Long?`
  - `fun nextRing(tasks: List<ReminderInput>, nowMillis: Long, zone: ZoneId, morningMinute: Int = MORNING_MINUTE): Ring?` — the earliest ring **strictly after** `nowMillis`, carrying every task that shares that instant, ids ascending.

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val zone = ZoneId.of("Europe/Prague")
private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
    LocalDate.of(y, m, d).atTime(h, min).atZone(zone).toInstant().toEpochMilli()
private val day = LocalDate.of(2026, 12, 2).toEpochDay()

@Test fun aTimedTaskRingsAtItsMinute() =
    assertEquals(at(2026, 12, 2, 15, 0), ringAt(ReminderInput(1, day, 15 * 60, null), zone))
@Test fun anUntimedTaskRingsAtEight() =
    assertEquals(at(2026, 12, 2, 8, 0), ringAt(ReminderInput(1, day, null, null), zone))
@Test fun aSnoozeAheadOfTheDueTimeWins() =
    assertEquals(at(2026, 12, 2, 16, 0), ringAt(ReminderInput(1, day, 15 * 60, at(2026, 12, 2, 16, 0)), zone))
@Test fun aTaskWithNoDateNeverRings() =
    assertNull(ringAt(ReminderInput(1, null, 15 * 60, null), zone))
@Test fun aTaskWithNoDateButASnoozeStillNeverRings() =   // an Inbox item cannot be snoozed into ringing
    assertNull(ringAt(ReminderInput(1, null, null, at(2026, 12, 2, 16, 0)), zone))

@Test fun theEarliestFutureRingWins()
@Test fun everyTaskSharingTheEarliestInstantIsInTheRing() {
    val tasks = listOf(ReminderInput(7, day, null, null), ReminderInput(3, day, null, null), ReminderInput(9, day, 15 * 60, null))
    val ring = nextRing(tasks, at(2026, 12, 2, 7, 0), zone)
    assertEquals(at(2026, 12, 2, 8, 0), ring!!.atMillis)
    assertEquals(listOf(3L, 7L), ring.taskIds)
}
@Test fun aRingThatHasPassedIsSkipped() {               // Review Focus 5
    val tasks = listOf(ReminderInput(1, day, null, null))   // 08:00, and it is now 10:00
    assertNull(nextRing(tasks, at(2026, 12, 2, 10, 0), zone))
}
@Test fun theInstantItselfIsNotInTheFuture() =          // the receiver has just posted it
    assertNull(nextRing(listOf(ReminderInput(1, day, 8 * 60, null)), at(2026, 12, 2, 8, 0), zone))
@Test fun aPastRingDoesNotHideALaterOne()               // one task at 08:00 and one at 15:00, now 10:00 → the 15:00 one
@Test fun nothingComingIsNull() = assertNull(nextRing(emptyList(), at(2026, 12, 2, 10, 0), zone))
@Test fun aSnoozeInThePastIsIgnoredAndTheDueTimeStands()
@Test fun theHourSkippedByTheSpringForwardStillGivesAnInstant()
    // 2027-03-28 02:30 does not exist in Europe/Prague; ringAt must return a real instant, not throw
```

- [ ] **Step 2: Run them and watch them fail.**

Run: `gradlew.bat testDebugUnitTest --tests "cz.olda.tasks.data.reminder.ReminderPlanTest"`
Expected: FAIL — unresolved reference.

- [ ] **Step 3: Implement `ringAt` and `nextRing`.**

`ringAt` builds `LocalDate.ofEpochDay(dueDate).atTime(hour, minute).atZone(zone)` — `atZone` is what resolves a skipped or repeated local hour rather than throwing — and takes `snoozedUntil` instead when it is the later of the two. `nextRing` maps, filters out nulls and anything not after `nowMillis`, then groups by instant and takes the minimum.

- [ ] **Step 4: Run them and watch them pass, then the whole unit suite.**

Run: as Step 2, then `gradlew.bat testDebugUnitTest`.
Expected: PASS; `DataLayerIsPlatformFreeTest` still green.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data/reminder app/src/test/java/cz/olda/tasks/data/reminder
git commit -m "feat: work out which minute a task rings at"
```

---

### Task 5: The next ring, straight out of the database

A `Flow` that re-emits on every write to `tasks`, which is what makes the alarm a function of the data rather than something to keep in step by hand.

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/data/dao/TaskDao.kt`
- Modify: `app/src/main/java/cz/olda/tasks/data/TaskRepository.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/data/NextReminderTest.kt`

**Interfaces:**
- Consumes: `ReminderInput`, `Ring`, `nextRing` (Task 4).
- Produces:
  - `TaskDao.observeReminderInputs(): Flow<List<ReminderInput>>` — `SELECT id AS taskId, dueDate, dueMinute, snoozedUntil FROM tasks WHERE deletedAt IS NULL AND done = 0 AND dueDate IS NOT NULL`
  - `TaskDao.setSnooze(id: Long, until: Long?, now: Long)` — `UPDATE tasks SET snoozedUntil = :until, updatedAt = :now WHERE id = :id AND deletedAt IS NULL`
  - `TaskRepository.observeNextReminder(): Flow<Ring?>`
  - `TaskRepository.snooze(id: Long, untilMillis: Long?)`

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun aTimedTaskAheadOfNowIsTheNextRing()
@Test fun aDoneTaskNeverRings()                       // Review Focus 3
@Test fun aSoftDeletedTaskNeverRings()                // Review Focus 3
@Test fun anUndatedTaskNeverRings()
@Test fun twoUntimedTasksOnTheSameDayShareOneRing()   // one Ring, both ids
@Test fun snoozingMovesTheRingAndLeavesTheDueTimeAlone()
    // snooze(+1h): the Ring moves, and the task's dueDate and dueMinute are untouched
@Test fun clearingASnoozePutsTheRingBack()
@Test fun completingTheOnlyDatedTaskLeavesNoRing()    // the flow emits null
@Test fun anOrdinaryEditDoesNotCancelASnooze()
    // snooze, then repository.update(task.copy(title = "...")), then the Ring is still the snoozed one
```

The clock is `Clock.fixed` at 2026-12-02 10:00 Europe/Prague, so a task at 15:00 is ahead of "now" and one at 08:00 is behind it.

- [ ] **Step 2: Run them and watch them fail.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.data.NextReminderTest`
Expected: FAIL. **Back up the database first.**

- [ ] **Step 3: Implement the query, the snooze write and the flow.**

`observeNextReminder` maps `observeReminderInputs()` through `nextRing(it, clock.millis(), clock.zone)`. A KDoc notes that `now` is read at each emission, which is why a write is what moves the ring, and that `snoozedUntil` is deliberately absent from `updateEditableAndFollow`'s column list so an edit cannot cancel a snooze.

- [ ] **Step 4: Run them and watch them pass.**

Run: as Step 2. Expected: PASS, 9 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data app/src/androidTest/java/cz/olda/tasks/data/NextReminderTest.kt
git commit -m "feat: the next ring comes out of the tasks table"
```

---

### Task 6: The notification, the channel and the permissions

The first Android-only task. Nothing schedules anything yet: this is the notification itself, provable on the phone.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/cz/olda/tasks/platform/Notifications.kt`
- Modify: `app/src/main/java/cz/olda/tasks/TasksApplication.kt`
- Modify: `app/src/main/java/cz/olda/tasks/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/platform/NotificationsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, in `Notifications.kt`:
  - `const val CHANNEL_ID = "reminders"`, `const val SUMMARY_ID = 0`, `const val GROUP_KEY = "cz.olda.tasks.reminders"`
  - `fun ensureChannel(context: Context)` — importance high, name and description from `strings.xml`; creating it twice is a no-op, so no state is kept.
  - `fun postReminder(context: Context, taskId: Long, title: String, listName: String)` — notification id `taskId.toInt()`, grouped, tap opens `MainActivity` with `EXTRA_TASK_ID`, auto-cancelling, with the Done and Snooze actions **added in Task 8** (this task posts the notification with no actions).
  - `fun postSummary(context: Context, count: Int)` — id `SUMMARY_ID`, `setGroupSummary(true)`, text from the `reminder_summary` plural.
  - `fun cancelReminder(context: Context, taskId: Long)`
  - `const val EXTRA_TASK_ID = "cz.olda.tasks.taskId"`, read by `MainActivity` and handed to the nav host so a tap opens that task's detail.
- New strings: `reminder_channel_name`, `reminder_channel_description`, `plurals/reminder_summary` ("%d tasks today").

- [ ] **Step 1: Add the four permissions to the manifest.**

Exactly the block in the spec's Permissions section, `SCHEDULE_EXACT_ALARM` carrying `android:maxSdkVersion="32"`. No other permission.

- [ ] **Step 2: Write the failing test.**

`NotificationsTest.kt`, with `@get:Rule val permission = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)` so the instrumentation grants what a real first run asks for:

```kotlin
@Test fun theChannelExistsAfterEnsureChannel()        // NotificationManager.getNotificationChannel(CHANNEL_ID) is not null
@Test fun aPostedReminderShowsTheTitleAndTheList()
    // activeNotifications contains id 42; EXTRA_TITLE is the task's title, EXTRA_TEXT the list name
@Test fun aPostedReminderIsInTheGroup()               // its Notification.getGroup() == GROUP_KEY
@Test fun theSummaryIsItsOwnNotification()           // activeNotifications contains SUMMARY_ID
@Test fun cancelTakesItAway()
@After fun clear()                                    // cancelAll(), so one test cannot see another's notifications
```

- [ ] **Step 3: Run it and watch it fail.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.platform.NotificationsTest`
Expected: FAIL. **Back up the database first** (the run reinstalls the app).

- [ ] **Step 4: Implement `Notifications.kt`, call `ensureChannel` from `TasksApplication.onCreate`, and read `EXTRA_TASK_ID` in `MainActivity`.**

`NotificationCompat.Builder` with the small icon from `AppIcons`' existing drawable set or `R.mipmap.ic_launcher`; `PendingIntent.getActivity` with `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`. `MainActivity` passes the extra into the composition as a one-shot start destination argument — the existing nav host already has a task-detail route with an id.

- [ ] **Step 5: Run it and watch it pass.**

Run: as Step 3. Expected: PASS, 5 tests.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/cz/olda/tasks/platform app/src/main/java/cz/olda/tasks/TasksApplication.kt app/src/main/java/cz/olda/tasks/MainActivity.kt app/src/main/res/values/strings.xml app/src/androidTest/java/cz/olda/tasks/platform
git commit -m "feat: the reminder notification and its channel"
```

---

### Task 7: Booking the alarm and firing it

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/platform/AlarmBooking.kt`
- Create: `app/src/main/java/cz/olda/tasks/platform/ReminderScheduler.kt`
- Create: `app/src/main/java/cz/olda/tasks/platform/ReminderSync.kt`
- Create: `app/src/main/java/cz/olda/tasks/platform/ReminderReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/java/cz/olda/tasks/TasksApplication.kt`, `app/src/main/java/cz/olda/tasks/AppContainer.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/platform/ReminderSyncTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/platform/ReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `Ring`, `TaskRepository.observeNextReminder` (Tasks 4, 5); `Notifications` (Task 6).
- Produces:
  - `interface AlarmBooking { fun book(ring: Ring); fun cancel() }` — so a test can record what would have been booked instead of waiting for a real alarm.
  - `class ReminderScheduler(private val context: Context) : AlarmBooking` — `setExactAndAllowWhileIdle(RTC_WAKEUP, ring.atMillis, pendingIntent)`; the intent carries `EXTRA_AT_MILLIS` and the task ids, one request code for the whole app so a new booking replaces the old.
  - `class ReminderSync(private val scope: CoroutineScope, private val repository: TaskRepository, private val booking: AlarmBooking)` with `fun start()` (collect `observeNextReminder()`, booking each emission and cancelling on null) and `suspend fun rearmNow()` (one read, for the receivers, which have no process to collect in).
  - `class ReminderReceiver : BroadcastReceiver()` — posts a reminder for every id in the fired ring plus the summary when there is more than one, then re-arms. It reads each task's title and list through the container's repository, and skips an id whose task is gone or already done.
- `AppContainer` gains `reminderSync: ReminderSync` and an application-lifetime `CoroutineScope`; `TasksApplication.onCreate` calls `reminderSync.start()`. The receiver is declared in the manifest, not exported.

- [ ] **Step 1: Write the failing tests.**

`ReminderSyncTest.kt`, with a recording `AlarmBooking` and an in-memory database:

```kotlin
@Test fun startingBooksTheNextRing()
@Test fun addingAnEarlierTaskRebooksTheAlarm()        // the recorder's last booking is the new instant
@Test fun completingTheLastDatedTaskCancelsTheAlarm()
@Test fun rearmNowBooksWithoutCollecting()
```

`ReminderReceiverTest.kt`, calling `onReceive` directly with an intent built the way the scheduler builds one — driving a real alarm would make the suite wait on the clock:

```kotlin
@Test fun oneTaskInTheRingPostsOneNotification()
@Test fun threeTasksInTheRingPostThreeNotificationsAndASummary()   // Review Focus 1
@Test fun aTaskCompletedBeforeTheAlarmFiredPostsNothing()
@Test fun aTaskDeletedBeforeTheAlarmFiredPostsNothing()
```

- [ ] **Step 2: Run them and watch them fail.**

Run: the two `-Pandroid.testInstrumentationRunnerArguments.class=` commands.
Expected: FAIL. **Back up the database first.**

- [ ] **Step 3: Implement the four files and wire them up.**

`ReminderSync.start()` is idempotent (a second call does not collect twice). The receiver uses `goAsync()` and finishes the pending result after posting and re-arming, because a receiver cannot wait for a coroutine on its own.

- [ ] **Step 4: Run them and watch them pass.**

Run: as Step 2. Expected: PASS, 8 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/platform app/src/main/AndroidManifest.xml app/src/main/java/cz/olda/tasks/TasksApplication.kt app/src/main/java/cz/olda/tasks/AppContainer.kt app/src/androidTest/java/cz/olda/tasks/platform
git commit -m "feat: book one exact alarm and fire the reminders in it"
```

---

### Task 8: Done and Snooze on the notification

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/platform/ReminderActionReceiver.kt`
- Modify: `app/src/main/java/cz/olda/tasks/platform/Notifications.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/platform/ReminderActionReceiverTest.kt`

**Interfaces:**
- Consumes: `TaskRepository.setDone` and `.snooze` (Tasks 3, 5); `Notifications` (Task 6); `ReminderSync.rearmNow` (Task 7).
- Produces:
  - `class ReminderActionReceiver : BroadcastReceiver()` with `ACTION_DONE` and `ACTION_SNOOZE`, the task id in the intent's **data** (`tasks://reminder/<id>/done`) so two `PendingIntent`s for the same task cannot collide, and `const val SNOOZE_MINUTES = 60`.
  - `Notifications.postReminder` gains the two actions.
- New strings: `reminder_done`, `reminder_snooze`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun doneCompletesTheTaskAndCancelsTheNotification()
@Test fun doneOnARecurringTaskCreatesTheNextOccurrence()   // the whole point of one completion door
@Test fun snoozeMovesTheRingAnHourOnAndLeavesTheDueTimeAlone()
@Test fun snoozeTwiceAddsAnotherHour()
@Test fun anActionForATaskThatIsGoneDoesNothingAndDoesNotThrow()
```

- [ ] **Step 2: Run them and watch them fail.** **Back up the database first.**

Run: `gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cz.olda.tasks.platform.ReminderActionReceiverTest`

- [ ] **Step 3: Implement the receiver and add the actions.**

`goAsync()` again; the snooze time is `clock.millis() + SNOOZE_MINUTES * 60_000`, read from the container's clock so a test can fix it.

- [ ] **Step 4: Run them and watch them pass.** Expected: PASS, 5 tests.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/platform app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/androidTest/java/cz/olda/tasks/platform
git commit -m "feat: Done and Snooze on a reminder"
```

---

### Task 9: Re-arming after a restart, a clock change and a reinstall

Four intent filters, one receiver. `MY_PACKAGE_REPLACED` is the one that matters most while this phase is being built: every `installDebug` cancels the app's alarms.

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/platform/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/platform/BootReceiverTest.kt`

**Interfaces:**
- Consumes: `ReminderSync.rearmNow` (Task 7).
- Produces: `class BootReceiver : BroadcastReceiver()`, declared with `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET` and `MY_PACKAGE_REPLACED`.

- [ ] **Step 1: Write the failing test.**

```kotlin
@Test fun eachOfTheFourIntentsReBooksTheNextRing() {
    listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED,
           Intent.ACTION_TIME_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)
        .forEach { action -> /* onReceive, then the recorder holds the expected instant */ }
}
@Test fun anUnrelatedBroadcastBooksNothing()
```

- [ ] **Step 2: Run it and watch it fail.** **Back up the database first.**

- [ ] **Step 3: Implement the receiver and declare the four filters.**

- [ ] **Step 4: Run it and watch it pass.**

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/platform app/src/main/AndroidManifest.xml app/src/androidTest/java/cz/olda/tasks/platform/BootReceiverTest.kt
git commit -m "feat: re-arm the alarm after a boot, a clock change or a reinstall"
```

---

### Task 10: Asking for notifications, once

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/platform/NotificationPermission.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/settings/SettingsScreen.kt`, `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`, `app/src/main/java/cz/olda/tasks/ui/AppShell.kt` (or wherever the request is hung), `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` and its `NOTIFICATIONS_ASKED` key (Task 1).
- Produces:
  - `@Composable fun AskForNotificationsOnce(asked: Boolean, onAsked: () -> Unit)` — launches the `POST_NOTIFICATIONS` request on first composition when `asked` is false and the API is 33+, then calls `onAsked()` whatever the answer; on API 29–32 it only calls `onAsked()`.
  - `fun notificationsEnabled(context: Context): Boolean` — `NotificationManagerCompat.from(context).areNotificationsEnabled()`.
  - `class SettingsViewModel(private val settings: SettingsRepository, private val notificationsEnabled: () -> Boolean)` exposing `val state: StateFlow<SettingsState>` with `asked: Boolean` and `remindersBlocked: Boolean` (true only when asked and not enabled), and `fun markAsked()`.
  - The Settings row: text from `reminders_blocked`, a button from `open_notification_settings` that starts `Settings.ACTION_APP_NOTIFICATION_SETTINGS`, both inside `testTag("settings-reminders-blocked")`.

- [ ] **Step 1: Write the failing tests.**

The screen takes the state, so the test needs no system switch:

```kotlin
@Test fun theBlockedRowIsHiddenWhenNotificationsWork()
@Test fun theBlockedRowIsHiddenBeforeTheQuestionHasBeenAsked()   // asked = false, enabled = false
@Test fun theBlockedRowShowsOnceAskedAndStillBlocked()
@Test fun theColourSectionIsStillThere()                          // Phase 1's picker is untouched
```

- [ ] **Step 2: Run them and watch them fail.** **Back up the database first.**

- [ ] **Step 3: Implement the permission helper, the ViewModel and the row.**

- [ ] **Step 4: Run them and watch them pass.**

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/platform app/src/main/java/cz/olda/tasks/ui app/src/main/res/values/strings.xml app/src/androidTest/java/cz/olda/tasks/ui/settings
git commit -m "feat: ask for notifications once and say so when they are blocked"
```

---

### Task 11: The repeat picker

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/task/RecurrenceLabel.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/task/RepeatRules.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/task/RepeatPickerSheet.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/task/TaskDetailScreen.kt`, `app/src/main/java/cz/olda/tasks/ui/task/TaskDetailViewModel.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/cz/olda/tasks/ui/task/RecurrenceLabelTest.kt` (new cases)
- Test: `app/src/test/java/cz/olda/tasks/ui/task/RepeatRulesTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/task/RepeatPickerSheetTest.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/task/TaskDetailScreenTest.kt` (new cases)

**Interfaces:**
- Consumes: `recurrenceLabel` as it stands.
- Produces:
  - `recurrenceLabel` reads `FREQ=MONTHLY;INTERVAL=n` and `FREQ=YEARLY;INTERVAL=n`, returning the new `R.string.repeat_every_n_months` and `repeat_every_n_years`; everything it already refused still returns `repeat_unknown`.
  - In `RepeatRules.kt`, both pure and JVM-tested:
    - `enum class RepeatUnit { DAYS, WEEKS, MONTHS, YEARS }`
    - `fun presetRules(anchor: LocalDate): List<String>` — `["FREQ=DAILY", "FREQ=WEEKLY;BYDAY=<anchor's weekday>", "FREQ=MONTHLY", "FREQ=YEARLY"]`
    - `fun customRule(interval: Int, unit: RepeatUnit): String` — the `INTERVAL` is omitted when `interval` is 1, so the picker and the presets produce the same string for the same meaning
    - `fun customOf(rule: String): Pair<Int, RepeatUnit>?` — what Custom's fields open on, null when the rule is a preset or unreadable
  - `@Composable fun RepeatPickerSheet(current: String?, anchor: LocalDate, onPick: (String?) -> Unit, onDismiss: () -> Unit)` — the five rows plus `Custom…`, each row labelled through `recurrenceLabel`'s own strings, the current rule ticked, test tags `repeat-option-none`, `repeat-option-<rule>`, `repeat-option-custom`, and `repeat-custom-interval` / `repeat-custom-unit` / `repeat-custom-save` inside Custom.
  - `TaskDetailViewModel.setRecurrence(rule: String?)` replacing nothing — `clearRecurrence()` stays, as the ✕ still uses it.
  - Task detail's `RepeatRow` is **always** drawn, reading `repeat_none` ("Does not repeat") when the task has no rule, and opening the sheet when tapped.
- New strings: `repeat_every_n_months`, `repeat_every_n_years`, `repeat_none`, `repeat_custom`, `repeat_unit_days`, `repeat_unit_weeks`, `repeat_unit_months`, `repeat_unit_years`.

- [ ] **Step 1: Write the failing pure tests.**

In `RecurrenceLabelTest.kt`: `FREQ=MONTHLY;INTERVAL=3` → `repeat_every_n_months` with interval 3; `FREQ=YEARLY;INTERVAL=2` → `repeat_every_n_years`; `FREQ=MONTHLY;BYDAY=MO` still `repeat_unknown`.
In `RepeatRulesTest.kt`: a Tuesday anchor gives `FREQ=WEEKLY;BYDAY=TU`; `customRule(1, DAYS)` is `FREQ=DAILY`; `customRule(3, MONTHS)` is `FREQ=MONTHLY;INTERVAL=3`; `customOf("FREQ=DAILY;INTERVAL=2")` is `2 to DAYS`; `customOf("FREQ=DAILY")` is null; `customOf("rubbish")` is null; every `customRule` output round-trips through `recurrenceLabel` to something other than `repeat_unknown`.

- [ ] **Step 2: Run them and watch them fail.**

Run: `gradlew.bat testDebugUnitTest --tests "cz.olda.tasks.ui.task.*"`

- [ ] **Step 3: Implement `RepeatRules.kt` and the two new label branches.**

- [ ] **Step 4: Run them and watch them pass.**

- [ ] **Step 5: Write the failing screen tests.**

`RepeatPickerSheetTest`: the current rule is ticked; tapping "Every day" calls `onPick("FREQ=DAILY")`; tapping "Does not repeat" calls `onPick(null)`; the weekly row names the anchor's weekday; Custom opens on the current custom rule and saves `FREQ=WEEKLY;INTERVAL=2`.
`TaskDetailScreenTest`: a task with no rule shows "Does not repeat"; tapping the row opens the sheet; the ✕ on a task with a rule still clears it.

- [ ] **Step 6: Run them and watch them fail.** **Back up the database first.**

- [ ] **Step 7: Implement the sheet and the always-present row.**

The sheet is a `ModalBottomSheet`, and the pop and spring vocabulary comes from `ui/theme/Motion.kt` as the rest of the app does.

- [ ] **Step 8: Run them and watch them pass.**

- [ ] **Step 9: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/ui/task app/src/main/res/values/strings.xml app/src/test/java/cz/olda/tasks/ui/task app/src/androidTest/java/cz/olda/tasks/ui/task
git commit -m "feat: a repeat picker on task detail"
```

---

### Task 12: The quick-add repeat chip, and the wart it fixes

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddChips.kt`, `QuickAddViewModel.kt`, `QuickAddSheet.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddViewModelTest.kt` (new cases)
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/quickadd/QuickAddSheetTest.kt` (new cases)

**Interfaces:**
- Consumes: `RepeatPickerSheet`, `recurrenceLabel`, `presetRules` (Task 11).
- Produces:
  - `QuickAddViewModel.setRecurrence(rule: String?)` replacing `clearRecurrence()`; two saved-state keys, `REPEAT_PINNED: Boolean` and `REPEAT_RULE: String?`, replacing `NO_REPEAT`. While pinned, the picked rule wins over anything typed; clearing the field unpins it, exactly as the date and time pins already work.
  - `QuickAddState.recurrence: String?` (unchanged) and `recurrenceText: String?` — Olda's own typed words when the parser found the repeat, and `null` when the rule came from the picker, in which case the chip renders `recurrenceLabel` instead.
  - The repeat chip is drawn **always**, reading `repeat_chip_empty` ("↺ Repeat") when there is no rule, and opening the picker when tapped.
- New string: `repeat_chip_empty`.

- [ ] **Step 1: Write the failing tests.**

ViewModel: picking a rule pins it and typing afterwards does not change it; picking "Does not repeat" pins "no repeat" and a typed `kazdy den` no longer sets one; clearing the field unpins, so `kazdy den` works again; saving writes the pinned rule; the chip's text is the typed words when parsed and the English label when picked.
Sheet: the chip is visible with an empty field; tapping it opens the picker; picking "Every week on Tuesday" shows that on the chip; after picking "Does not repeat" the chip is still there and can be tapped again — **the wart from Phase 2, named in a test**.

- [ ] **Step 2: Run them and watch them fail.** **Back up the database first.**

- [ ] **Step 3: Implement the state, the chip and the sheet wiring.**

- [ ] **Step 4: Run them and watch them pass, then the whole quick-add device package.**

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/ui/quickadd app/src/main/res/values/strings.xml app/src/androidTest/java/cz/olda/tasks/ui/quickadd
git commit -m "feat: the quick-add repeat chip opens the picker and never disappears"
```

---

### Task 13: The repeat mark on a row

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppIcons.kt`, `app/src/main/java/cz/olda/tasks/ui/components/TaskRow.kt`, `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/cz/olda/tasks/ui/AppIconsTest.kt` (one new case)
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/components/TaskRowTest.kt` (new cases)

**Interfaces:**
- Consumes: nothing.
- Produces: `AppIcons.Repeat: ImageVector`; `TaskRow` draws it between the title and the list name when `item.task.recurrence != null`, tagged `task-repeat-<id>`, tinted `onSurfaceVariant`, with `contentDescription` from the new `repeat_mark` string. It must take no width when absent, and the row's existing width arithmetic (which measures the title, the time column and the list name) must account for it when present.

- [ ] **Step 1: Write the failing tests.**

`AppIconsTest`: the new icon follows whatever rule the existing cases assert (a 24×24 viewport and a non-empty path).
`TaskRowTest`: a task with `FREQ=DAILY` shows `task-repeat-<id>`; a task without a rule does not; a long title beside the mark still shows the list name (the width arithmetic).

- [ ] **Step 2: Run them and watch them fail.** **Back up the database first.**

- [ ] **Step 3: Draw the icon and add the mark.**

- [ ] **Step 4: Run them and watch them pass, then the whole components device package** — `TaskRow` is on every screen, and its layout maths is the most fragile code in the app.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/ui app/src/main/res/values/strings.xml app/src/test/java/cz/olda/tasks/ui/AppIconsTest.kt app/src/androidTest/java/cz/olda/tasks/ui/components
git commit -m "feat: a repeat mark on every recurring row"
```

---

### Task 14: Moving repeats aside in the repository

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/data/TaskRepository.kt`
- Modify: `app/src/main/java/cz/olda/tasks/ui/today/TodayViewModel.kt`, `app/src/main/java/cz/olda/tasks/ui/upcoming/UpcomingViewModel.kt`, `app/src/main/java/cz/olda/tasks/ui/AppViewModelProvider.kt`
- Test: `app/src/androidTest/java/cz/olda/tasks/data/TaskRepositoryTest.kt` (new cases)
- Test: `app/src/test/java/cz/olda/tasks/ui/today/TodayViewModelTest.kt`, `app/src/test/java/cz/olda/tasks/ui/upcoming/UpcomingViewModelTest.kt` (new cases)

**Interfaces:**
- Consumes: `SettingsRepository.HIDE_REPEATS` (Task 1).
- Produces:
  - `DaySections` gains `val hiddenRepeats: List<TaskWithList> = emptyList()`
  - `UpcomingDay` gains `val hiddenRepeats: List<TaskWithList> = emptyList()`
  - `TaskRepository.observeDay(day: LocalDate, hideRepeats: Boolean = false)` and `observeUpcoming(hideRepeats: Boolean = false)` — with `false` nothing changes anywhere, which is what keeps every existing test honest. With `true`, a task whose `recurrence` is non-null leaves `timed` and `anytime` for `hiddenRepeats`, in the order it had; `overdue` and `tomorrow` are never touched.
  - `TodayViewModel(repository, clock, settings: SettingsRepository)` and `UpcomingViewModel(repository, settings)`, each combining the flag into the flow they already build, plus `fun setHideRepeats(hide: Boolean)`. `TodayState` and Upcoming's state carry `hideRepeats: Boolean` so the chip can draw itself.
  - The JVM tests fake the settings store by subclassing `SettingDao()`, the way `FakeTaskDao` subclasses `TaskDao()` — which is why Task 1 makes it an abstract class.

- [ ] **Step 1: Write the failing repository tests.**

```kotlin
@Test fun withRepeatsShownNothingMoves()
@Test fun aTimedRepeatMovesToHiddenRepeats()
@Test fun anAnytimeRepeatMovesToHiddenRepeats()
@Test fun anOverdueRepeatStaysWhereItIs()                // the spec's own rule
@Test fun tomorrowKeepsCountingRepeats()
@Test fun hiddenRepeatsKeepTheirOrder()
@Test fun upcomingMovesRepeatsPerDay()
@Test fun aDayWhoseOnlyTasksAreRepeatsHasEmptySectionsAndOneHiddenRepeat()   // Review Focus 4
```

- [ ] **Step 2: Write the failing ViewModel tests.**

Today: the flag reaches the repository and the state carries it; `setHideRepeats(true)` writes through the settings store and the state follows. Upcoming: the same.

- [ ] **Step 3: Run both and watch them fail.** **Back up the database first.**

- [ ] **Step 4: Implement the filtering and the ViewModel wiring.**

- [ ] **Step 5: Run both and watch them pass, then the whole unit suite** — `DataLayerIsPlatformFreeTest` included, since `data/` changed.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/data/TaskRepository.kt app/src/main/java/cz/olda/tasks/ui app/src/androidTest/java/cz/olda/tasks/data/TaskRepositoryTest.kt app/src/test/java/cz/olda/tasks/ui
git commit -m "feat: the repository moves repeats aside instead of dropping them"
```

---

### Task 15: The chip, the folded row, and the numbers that must not move

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/today/TodayScreen.kt`, `app/src/main/java/cz/olda/tasks/ui/upcoming/UpcomingScreen.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/components/RepeatsChip.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/components/FoldedRepeatsRow.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/today/TodayScreenTest.kt`, `app/src/androidTest/java/cz/olda/tasks/ui/upcoming/UpcomingScreenTest.kt` (new cases)

**Interfaces:**
- Consumes: `DaySections.hiddenRepeats`, `UpcomingDay.hiddenRepeats`, `setHideRepeats` (Task 14).
- Produces:
  - `@Composable fun RepeatsChip(hidden: Boolean, onToggle: (Boolean) -> Unit)` — under the week strip on Today and under the header on Upcoming, reading `repeats_hide` / `repeats_hidden`, dark when on, tagged `repeats-chip`.
  - `@Composable fun FoldedRepeatsRow(count: Int, expanded: Boolean, onToggle: () -> Unit)` — "↺ %d repeats hidden ›" from the new `plurals/repeats_hidden_count`, tagged `folded-repeats`; the expansion is the screen's own `rememberSaveable` state, so it lasts the visit and no longer.
  - The three numbers the chip must not change: `DayGlance` takes `sections.timed + sections.anytime + sections.hiddenRepeats`; the empty state's condition gains `hiddenRepeats`; `observeDots` is left alone with a comment saying so.
- New strings: `repeats_hide`, `repeats_hidden`, `plurals/repeats_hidden_count`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
// TodayScreenTest
@Test fun theChipIsThereAndSaysWhatItWillDo()
@Test fun tappingTheChipAsksTheViewModelToHideRepeats()
@Test fun withRepeatsHiddenTheFoldedRowCountsThem()
@Test fun tappingTheFoldedRowShowsThemAndLeavesTheChipOn()      // the peek
@Test fun theGlanceLineStillCountsHiddenRepeats()               // Review Focus 4
@Test fun nothingDueTodayDoesNotShowAboveAFoldedRow()           // Review Focus 4
@Test fun anOverdueRepeatIsStillOnScreenWithRepeatsHidden()
// UpcomingScreenTest
@Test fun eachDayGetsItsOwnFoldedRow()
@Test fun aDayWithNoRepeatsHasNoFoldedRow()
```

- [ ] **Step 2: Run them and watch them fail.** **Back up the database first.**

- [ ] **Step 3: Implement the chip, the folded row and the three number fixes.**

- [ ] **Step 4: Run both screen classes and watch them pass.**

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/cz/olda/tasks/ui app/src/main/res/values/strings.xml app/src/androidTest/java/cz/olda/tasks/ui
git commit -m "feat: hide repeats behind one chip, with nothing lost"
```

---

### Task 16: On the phone

The phase is done when it works on Olda's own device with his own data, not when the suite is green.

**Files:**
- Modify: `docs/design.md` (tick the Phase 3 roadmap row)
- Create: a hand-over note in the shared notes folder, the way Phase 2 left one

- [ ] **Step 1: Run the whole unit suite.**

Run: `gradlew.bat testDebugUnitTest`. Expected: PASS, no skips.

- [ ] **Step 2: Back up his database, then run the whole device suite.**

Back up all three files to `.superpowers/sdd/db-backup/`, confirm the sizes, wake the phone, confirm it is not in his hands, then `gradlew.bat connectedDebugAndroidTest`.
Expected: PASS, 0 failures, and more tests than the 269 Phase 2 left.

- [ ] **Step 3: Restore his database and check it.**

`installDebug` first, then `run-as cz.olda.tasks mkdir -p` the databases directory, push to `/data/local/tmp` and `run-as ... cp` into place (a direct `adb push` into the app's data directory is refused). Open the app and confirm his 31 tasks are there, with their lists and tags.

- [ ] **Step 4: Watch a real reminder fire.**

Make a task due two minutes out, lock the phone, and wait. The notification must arrive at that minute; Snooze must bring it back an hour later (verify the stored `snoozedUntil` rather than waiting); Done must tick it off without opening the app. Then make a daily task due two minutes out, let it ring, tap Done, and confirm tomorrow's copy exists with the same time and tags.

- [ ] **Step 5: Reinstall and confirm the alarm survives it.**

With a reminder pending, run `gradlew.bat installDebug` and confirm the reminder still fires — the `MY_PACKAGE_REPLACED` filter from Task 9, and the one thing most likely to be mistaken for a code bug later.

- [ ] **Step 6: Tell Olda what to check himself**, in plain language: the notification, its two buttons, a repeat he ticks, the chip on Today and Upcoming, and that his 31 tasks are untouched.

- [ ] **Step 7: Commit and push.**

```bash
git add docs/design.md
git commit -m "docs: phase 3 is done and in use"
git push
```

---

## Self-review notes

- **Spec coverage.** Permissions → Task 6; ring times → 4; the alarm and its firing → 7; Done and Snooze → 8; re-arming → 9; the permission prompt and the Settings row → 10; `nextOccurrence` → 2; completing a repeat, the copy, the undo → 3; the picker and the two new rules → 11; the quick-add chip → 12; the repeat mark → 13; hiding, the folded row, the numbers → 14 and 15; the migration and the settings store → 1; the device run and his data → 16.
- **Interfaces.** `Ring` is `(atMillis, taskIds)` everywhere (Tasks 4, 5, 7). `setDone` returns `Long?` from Task 3 on. `hiddenRepeats` is a `List<TaskWithList>`, never a count, in Tasks 14 and 15. `SettingDao` and `TaskDao` are both `abstract class`, which Task 14's JVM tests depend on.
- **Review Focus coverage.** 1 → Tasks 4 and 7; 2 → Tasks 2 and 3; 3 → Task 5; 4 → Tasks 14 and 15; 5 → Task 4.
- **Order.** Task 1 must come first (every later task needs the column or the table). Tasks 2–5 are pure or data-layer and could be done in any order among themselves. Task 6 before 7 before 8 and 9. Task 11 before 12. Tasks 14 before 15. Task 16 last.
