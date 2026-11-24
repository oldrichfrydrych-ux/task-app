# Task app — rules for Claude Code

Olda's personal Android task app. He is new to Android development: you write most of the code, he steers and tests on his phone.

## Before any work
- Read `docs/design.md` (the spec) and the current plan in `docs/superpowers/plans/`.
- Work through the plan task by task, in order. Don't start a later phase's work early.

## Fixed choices
- Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, one `app` module (until Phase 8).
- Keep the core ready for Windows (Phase 8): no `android.*` imports and no `Context` in `data/` or the parser. Android-only work goes in its own small files.
- Package `cz.olda.tasks`, minimum SDK 29.
- Code identifiers in English; user-facing text only in `res/values/strings.xml`.
- No dynamic colour. No `INTERNET` permission before Phase 4.

## How to work
- Test first: write the failing test, run it, implement, run again.
- Commands (Windows: `gradlew.bat` instead of `./gradlew`):
  - unit tests: `./gradlew testDebugUnitTest`
  - device tests (phone connected): `./gradlew connectedDebugAndroidTest`
  - install on the phone: `./gradlew installDebug`
- Small files with one job each. Keep the codebase easy to follow (an anti-goal in the spec).
- Ask Olda before adding any new library or permission.
- Commit at the end of each task with a clear message, then push.
- Never commit `local.properties`, keystores or API keys.

## Talking to Olda
- After each task, explain in plain language what changed and what he should check on his phone.
- If something needs his hands (a setting on the phone, an account, a click in Android Studio), say exactly what to do.
