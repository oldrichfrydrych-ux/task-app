# Phase 0 · Setup and look — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. If the superpowers plugin is not installed, work through the tasks in order yourself.

**Goal:** An empty but real app on Olda's phone: four tabs, a quick-add sheet, and the look Olda picked, with tests and a GitHub repo in place.

**Architecture:** One Android Studio project, single `app` module. Compose UI with a `Scaffold`, a bottom `NavigationBar` and a floating add button. Tab state lives in `rememberSaveable`; no navigation library until Phase 1 needs one.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL with the version catalog the wizard creates, JUnit 4 and Compose UI tests.

**Spec:** `docs/design.md` (live version: https://claude.ai/artifact/St9TAeWyo8ztnwEN1Tex6W)

**Who does what:** tasks marked **[Olda]** need his hands, phone or accounts. **[Claude Code]** tasks run on Olda's computer. **[Claude chat]** happens in the claude.ai conversation.

## Global Constraints

- Package name and applicationId: `cz.olda.tasks` (working name; the display name can change any time, but the package should be final before Phase 4).
- Minimum SDK: API 29 (Android 10). Olda's phone must run Android 10 or newer.
- Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, one `app` module.
- Code identifiers in English. All user-facing text lives in `res/values/strings.xml`, in English for now.
- Dynamic colour (Material You wallpaper colours) is off: the app uses its own palette.
- No `INTERNET` permission until Phase 4.
- A task is done only when `./gradlew testDebugUnitTest` passes and, with the phone connected, `./gradlew connectedDebugAndroidTest` passes (on Windows: `gradlew.bat …`).
- Commit at the end of every task and push to GitHub.

## Review Focus

1. **Back button while quick-add is open** → closes the sheet; the app stays open. Test in Task 3.
2. **Rotating the phone or leaving the app** → the selected tab is still selected afterwards. Test in Task 3.
3. **Phone in dark mode** → all text keeps at least 4.5:1 contrast against its background. Test in Task 5.
4. **Czech characters** (č, ř, ž, ů, ě, ď, ť, ň) → render correctly in the chosen font. Manual check in Task 5.
5. **Largest system font size** → tab labels stay readable and nothing overlaps. Manual check in Task 3.

---

### Task 1: Prepare the computer and the phone [Olda]

**Files:** none.

- [x] **Step 1: Install Android Studio** from https://developer.android.com/studio and finish the setup wizard (Standard install; it downloads the Android SDK).
  Check: Android Studio opens to its Welcome screen.
- [x] **Step 2: Install Git** (https://git-scm.com).
  Check: `git --version` in a terminal prints a version.
- [x] **Step 3: Install Claude Code and sign in**, following the official Claude Code quickstart on docs.claude.com. Confirm your Claude plan includes it.
  Check: `claude` starts in a terminal.
- [x] **Step 4: Check the phone's Android version:** Settings → About phone → Android version.
  Check: 10 or higher.
- [x] **Step 5: Turn on developer mode:** Settings → About phone → tap *Build number* seven times. Then Settings → System → Developer options → turn on *USB debugging*. Menu names vary slightly by phone brand.
- [x] **Step 6: Connect the phone by USB** and accept the "Allow USB debugging?" prompt on the phone.
  Check: in Android Studio, the phone's name appears in the device drop-down at the top.
- [x] **Step 7: Create a private GitHub repository** named `task-app` (no README, no .gitignore; the wizard makes those).

### Task 2: Create the project and see it on the phone [Olda, then Claude Code]

**Files:**
- Created by the wizard: `app/src/main/java/cz/olda/tasks/MainActivity.kt`, `app/src/main/java/cz/olda/tasks/ui/theme/{Color,Theme,Type}.kt`, `app/src/test/.../ExampleUnitTest.kt`, `app/src/androidTest/.../ExampleInstrumentedTest.kt`, `gradle/libs.versions.toml`
- Add from the starter pack: `CLAUDE.md`, `docs/design.md`, `docs/superpowers/plans/2026-11-23-phase-0-setup.md`

- [x] **Step 1 [Olda]: Run the wizard:** File → New → New Project → *Empty Activity*. Values:
  - Name: `Tasks`
  - Package name: `cz.olda.tasks`
  - Minimum SDK: `API 29 ("Q"; Android 10.0)`
  - Build configuration language: Kotlin DSL
  Check: the project opens and the first Gradle sync finishes without errors.
- [x] **Step 2 [Olda]: Press Run** (green ▶) with the phone selected.
  Check: the phone shows "Hello Android!".
- [x] **Step 3 [Olda]: Copy the starter pack** (`CLAUDE.md` and the `docs/` folder) into the project's root folder, next to `settings.gradle.kts`.
- [x] **Step 4 [Claude Code]: Run the wizard's tests.**
  Run: `./gradlew testDebugUnitTest` then `./gradlew connectedDebugAndroidTest` (phone connected).
  Expected: both end with `BUILD SUCCESSFUL`.
- [x] **Step 5 [Claude Code]: First commit and push.**
  ```bash
  git init -b main
  git add .
  git commit -m "chore: new Compose project with design doc and Phase 0 plan"
  git remote add origin https://github.com/<olda>/task-app.git
  git push -u origin main
  ```
  Check: `git status` is clean and `local.properties` was not committed (the wizard's `.gitignore` excludes it). The repo shows the files on GitHub.

### Task 3: App shell with four tabs and a quick-add sheet [Claude Code]

**Files:**
- Create: `app/src/main/java/cz/olda/tasks/ui/Destination.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/screens/PlaceholderScreen.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/quickadd/QuickAddSheet.kt`
- Modify: `app/src/main/java/cz/olda/tasks/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/cz/olda/tasks/ui/AppShellTest.kt`

**Interfaces:**
- Consumes: the wizard's theme function in `ui/theme/Theme.kt`. Rename it to `AppTheme` in this task.
- Produces:
  - `enum class Destination(@StringRes val label: Int, val icon: ImageVector) { Today, Upcoming, Inbox, Lists }`
  - `@Composable fun AppShell()`: `Scaffold` with a `NavigationBar` (one item per `Destination`, `Today` selected at start) and a FAB with content description `"Add task"` (`R.string.add_task`)
  - `@Composable fun PlaceholderScreen(destination: Destination)`: the root carries `Modifier.testTag("screen-${destination.name}")` and shows the label centred
  - `@Composable fun QuickAddSheet(onDismiss: () -> Unit)`: a `ModalBottomSheet` holding one `OutlinedTextField` with `testTag("quick-add-field")`, focused as soon as the sheet opens
  - `AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
- Strings: `today` "Today", `upcoming` "Upcoming", `inbox` "Inbox", `lists` "Lists", `add_task` "Add task", `quick_add_hint` "What needs doing?"

- [x] **Step 1: Write the failing tests** in `AppShellTest.kt` (`createComposeRule()`, each `setContent { AppTheme { AppShell() } }`):
  ```kotlin
  @Test fun showsFourTabs() { listOf("Today","Upcoming","Inbox","Lists").forEach { rule.onNodeWithText(it).assertIsDisplayed() } }
  @Test fun todayIsSelectedAtStart() { rule.onNodeWithText("Today").assertIsSelected() }
  @Test fun tappingInboxShowsInbox() { rule.onNodeWithText("Inbox").performClick(); rule.onNodeWithTag("screen-Inbox").assertIsDisplayed() }
  @Test fun addButtonOpensFocusedQuickAdd() { rule.onNodeWithContentDescription("Add task").performClick(); rule.onNodeWithTag("quick-add-field").assertIsFocused() }
  @Test fun backClosesQuickAdd() { rule.onNodeWithContentDescription("Add task").performClick(); Espresso.pressBack(); rule.onNodeWithTag("quick-add-field").assertDoesNotExist() }
  @Test fun selectedTabSurvivesRecreation() {
      val restore = StateRestorationTester(rule)
      restore.setContent { AppTheme { AppShell() } }
      rule.onNodeWithText("Inbox").performClick()
      restore.emulateSavedInstanceStateRestore()
      rule.onNodeWithText("Inbox").assertIsSelected()
  }
  ```
  (`selectedTabSurvivesRecreation` calls `restore.setContent` instead of `rule.setContent`.)
- [x] **Step 2: Run the tests and watch them fail.**
  Run: `./gradlew connectedDebugAndroidTest`
  Expected: compile errors for `AppShell`, `AppTheme`.
- [x] **Step 3: Implement** `Destination`, `PlaceholderScreen`, `QuickAddSheet`, `AppShell` and the `AppTheme` rename with the signatures above. Keep the selected tab in `rememberSaveable` (save the enum's `name`). Focus the field with a `FocusRequester` from a `LaunchedEffect(Unit)`. Make `MainActivity` call `enableEdgeToEdge()` and `setContent { AppTheme { AppShell() } }`.
- [x] **Step 4: Run the tests and watch them pass.**
  Run: `./gradlew connectedDebugAndroidTest` and `./gradlew testDebugUnitTest`
  Expected: `BUILD SUCCESSFUL`, 6 new tests pass.
- [x] **Step 5 [Olda]: Check by hand on the phone.** Tap each tab; open quick-add (the keyboard should appear immediately); press Back. Then set the phone's font size to the maximum and check that the tab labels are readable and nothing overlaps.
- [x] **Step 6: Commit and push.**
  ```bash
  git add app/
  git commit -m "feat: app shell with four tabs and quick-add sheet"
  git push
  ```

### Task 4: Pick the look [Claude chat, then Olda]

**Files:**
- Modify: `docs/design.md` (section *Look*)

- [x] **Step 1 [Claude chat]: Mock up visual directions** of Today, lists and quick-add, in light and dark. Done 23 Nov (mockups: https://claude.ai/artifact/7KbFvurP9WWc49FXhS2A2j).
- [x] **Step 2 [Olda]: Pick one direction.** Done 23 Nov: the warm "rail" direction, with List and Day views of Today.
- [x] **Step 3 [Claude chat]: Write the result into `docs/design.md` → *Look***: colour roles for light and dark, Manrope, corner radius, the 12 list colours. Done 23 Nov.
- [x] **Step 4 [Olda]: Commit** the updated `docs/design.md` (it comes in this starter pack).

### Task 5: Apply the chosen theme [Claude Code]

**Files:**
- Modify: `app/src/main/java/cz/olda/tasks/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/theme/ListPalette.kt`
- Create: `app/src/main/java/cz/olda/tasks/ui/theme/Contrast.kt`
- Create: `app/src/main/res/font/manrope_*.ttf` (Manrope from Google Fonts, SIL Open Font License; weights 500–800)
- Modify: `app/src/main/java/cz/olda/tasks/ui/AppShell.kt` (bottom bar becomes the floating pill from *Look*)
- Test: `app/src/test/java/cz/olda/tasks/ui/theme/ThemeColorsTest.kt`

**Interfaces:**
- Consumes: `AppTheme` from Task 3; colour values from `docs/design.md` → *Look*.
- Produces:
  - `internal val LightColorScheme: ColorScheme` and `internal val DarkColorScheme: ColorScheme` (Theme.kt), built from the *Look* values
  - `AppTheme` uses them and never uses dynamic colour
  - `data class ListColor(val name: String, val light: Color, val dark: Color)` and `val ListPalette: List<ListColor>`: the 12 preset list colours from *Look*, in its order (custom hex colours come in Phase 1, see *Colours*)
  - `fun contrastRatio(a: Color, b: Color): Float`: WCAG ratio from `Color.luminance()`, `(lighter + 0.05f) / (darker + 0.05f)`

- [x] **Step 1: Write the failing unit tests** in `ThemeColorsTest.kt`:
  ```kotlin
  @Test fun listPaletteHasTwelveDistinctColours() {
      assertEquals(12, ListPalette.size)
      assertEquals(12, ListPalette.map { it.light }.toSet().size)
      assertEquals(12, ListPalette.map { it.dark }.toSet().size)
      assertEquals(Color(0xFF3D6BE0), ListPalette.first().light)
  }
  @Test fun contrastOfBlackOnWhiteIs21() { assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f) }
  @Test fun lightTextIsReadable() {
      assertTrue(contrastRatio(LightColorScheme.onBackground, LightColorScheme.background) >= 4.5f)
      assertTrue(contrastRatio(LightColorScheme.onSurface, LightColorScheme.surface) >= 4.5f)
  }
  @Test fun darkTextIsReadable() {
      assertTrue(contrastRatio(DarkColorScheme.onBackground, DarkColorScheme.background) >= 4.5f)
      assertTrue(contrastRatio(DarkColorScheme.onSurface, DarkColorScheme.surface) >= 4.5f)
  }
  ```
- [x] **Step 2: Run them and watch them fail.**
  Run: `./gradlew testDebugUnitTest`
  Expected: compile errors for `ListPalette`, `contrastRatio`.
- [x] **Step 3: Implement** the colour schemes, Manrope typography, `ListPalette` and `contrastRatio` with the values from *Look*. Remove the dynamic-colour branch from `AppTheme`. Restyle the bottom bar in `AppShell` as the floating pill from *Look* (bottom bar colour, selected tab as a lighter pill with its label, coral add button at the right end). Keep each tab's selected/unselected semantics so Task 3's tests still find and select them.
- [x] **Step 4: Run all tests.**
  Run: `./gradlew testDebugUnitTest` and `./gradlew connectedDebugAndroidTest`
  Expected: `BUILD SUCCESSFUL`; Task 3's tests still pass.
- [x] **Step 5 [Olda]: Check by hand on the phone**, in both light and dark mode. Type "Příliš žluťoučký kůň úpěl ďábelské ódy" into quick-add: every letter must render in the app's font, with no fallback boxes or odd-looking glyphs.
- [x] **Step 6: Commit and push.**
  ```bash
  git add app/
  git commit -m "feat: apply chosen look (light and dark)"
  git push
  ```

**Phase 0 is done when** the app opens on Olda's phone in the chosen look, and both test commands pass.
