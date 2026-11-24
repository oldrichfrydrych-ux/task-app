# Phase 1 — what the next phase needs to know

Nov 24, 2026. Written when Phase 1 finished, from decisions made during the build that
would otherwise be lost with the scratch workspace. The git history holds the code; this
holds the things the code does not say.

## Before installing a new build on the phone

The database schema is **version 1** and `AppContainer` deliberately has **no
`fallbackToDestructiveMigration()`**. So any change to an `@Entity` — a column, an index,
anything that moves Room's identity hash — makes the app throw on first database access,
every launch, until the app is uninstalled or its data cleared.

That is the safe default: the alternative silently wipes real tasks. The rule it implies:

- **During a week of real use, do not change an entity.** A bug fix that needs one is a
  decision to make deliberately, knowing it costs the week's data.
- From Phase 2 onward, **ship a real `Migration(1, 2)`**. `app/schemas/…/1.json` is
  committed precisely so that is straightforward. Phase 2 (the parser) and Phase 3
  (recurrence) both touch `tasks`.

## There is no backup

`backup_rules.xml` and `data_extraction_rules.xml` **exclude `tasks.db`** (and its `-wal`,
`-shm`, `-journal`) from Android Auto Backup, on purpose: a live SQLite file copied with an
unmerged WAL is not a safe backup, and shipping one invites trusting it.

So the data lives on the phone and nowhere else. Real backup and export are Phase 6, as
the roadmap says. Until then, anything in this app that matters should also exist
somewhere else.

## What Phase 4 (Google Tasks sync) inherits

The schema was shaped for sync, but four things were left for Phase 4 to decide:

1. **A live task can point at a tombstoned list.** Deleting a list moves its live tasks to
   Inbox and soft-deletes the list row, but a *trashed* task keeps the dead list's id by
   design. Restoring such a task yields a task labelled with a list that no longer exists.
   Nothing is lost; the rule for reconciling it is Phase 4's.
2. **The Inbox row's `updatedAt` is 0** (it is seeded with the database). On a first
   reconcile a remote change would always win for Inbox. Probably fine; decide explicitly.
3. **`GroupDao.deleteKeepingLists` clears `lists.groupId` without stamping `updatedAt`.**
   Groups are app-only and `groupId` never reaches Google, so no push depends on it — but
   an app-level backup or merge would.
4. **`tasks.googleId` and `lists.googleListId` have unique indices** and both tables carry
   `deletedAt`/`updatedAt`, so tombstones and "newer change wins" are both expressible.

## Deliberate gaps in Phase 1, so nobody "fixes" them by accident

- **`SettingsScreen` exists and nothing navigates to it.** It is a try-it colour picker
  that stores nothing. A visible setting that forgets itself reads as broken, so it stays
  unreachable until something gives it state to persist. The route carries a comment
  saying so.
- **No completed-tasks view.** Completing a task removes it from every list; the undo
  snackbar is the only way back. This is why every query excludes done tasks.
- **Deleting is not undoable from the UI.** `TaskRepository.restore` exists and works, but
  nothing calls it — the confirmation dialogs carry the weight instead.
- **Subtasks are one level only**, because Google Tasks syncs one level.
- **A subtask's list and heading belong to its parent** and follow it; a subtask's own
  detail screen offers no list or heading picker.

## Two rules that cost real debugging to learn

- **The derived text shade is load-bearing, and it works because Compose blends in
  Oklab.** Four of the twelve preset list colours fail 4.5:1 contrast when used raw as
  text. `textShade()` darkens for light mode and lightens for dark until the shade clears
  4.5:1 — and a plain sRGB mix would only reach about 3.9:1 for a pale yellow, so the
  model would quietly fail. Do not "simplify" that interpolation.
- **`TaskRow` decides its layout by measured width, not font scale.** An earlier
  font-scale threshold let a long Czech word break mid-word at ordinary font size on a
  narrow screen. Czech produces long unbreakable words; any change to that row must keep
  the large-font tests, which assert line counts, clipping, overlap and containment from
  the real text layout.
