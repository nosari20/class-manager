# MaClasse — working notes for Claude

Android app for teachers (French *collège*/*lycée*). Single Gradle module, Kotlin + Compose,
MVVM, Room, offline only. Repo: https://github.com/nosari20/class-manager

## Non-negotiables

**The app is offline.** There is no `INTERNET` permission and no account. Do not add a
network dependency, an analytics SDK, or a crash reporter without the user explicitly asking
for it — the privacy claim in the README, PRIVACY.md and the Play listing all depend on this.

**Verify on the emulator, not just in tests.** Alarms, drag gestures, pickers, notifications
and navigation cannot be judged from unit tests. Build, install, drive the UI, and look at a
screenshot before claiming something works.

**Both string files change together.** `res/values/strings.xml` and `res/values-fr/strings.xml`
are key-for-key identical. Never hard-code user-visible text in Kotlin.

**Database changes** need: a version bump, a migration (auto where possible, hand-written when
a primary key or foreign key changes), the regenerated schema JSON in `app/schemas/`, **and** a
bump of `BackupManager.SCHEMA_VERSION`. Forgetting the last one silently accepts old backups
that then restore into a mismatched database.

**`targetSdk` stays at 35.** Robolectric refuses to run against a preview platform, so raising
it breaks the entire unit-test suite. `compileSdk` is 37 and that is fine.

## Build and test

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest   # build + 70 unit tests
.\gradlew.bat :app:lintDebug                              # must stay at 0 errors
```

Windows file locks: `Unable to delete directory …\build\generated\ksp\debug\kotlin` happens
regularly. Fix by deleting that directory and re-running — it is not a code error.

## Emulator

**A physical Pixel is sometimes plugged in.** Always set `$env:ANDROID_SERIAL="emulator-5554"`
before any `adb` call, or adb fails with "more than one device/emulator" — and never install
onto the physical device without asking.

UI automation helpers live in the session scratchpad (`ui.ps1`: `Dump-Ui`, `Find-Node`,
`Tap-Node`, `Tap-Bounds`, `List-Ui`, `Shot`). Two traps: `Find-Node` returns the *first*
substring match, so "Tirer" matches the hint text "Appuyez sur Tirer…" before the button —
prefer `Tap-Bounds` with coordinates from a fresh dump. And re-dump after any navigation,
because a stale dump silently taps the wrong place.

To inspect or edit app data, `sqlite3` is not on the emulator but is in the host's
`platform-tools`. Pull with `adb exec-out run-as edu.fnosari.classmanager cat databases/…`,
edit locally, push back via `/data/local/tmp` and `run-as … cp`. In Git Bash, set
`MSYS_NO_PATHCONV=1` for device paths or `/data/local/tmp` gets rewritten to a Windows path.

## Layout

```
app/src/main/java/edu/fnosari/classmanager/
├── AppContainer.kt   manual DI, built in ClassManagerApp.onCreate
├── MainActivity.kt   locale wrapping (attachBaseContext), theme, system bars
├── backup/           zip backup + AES-GCM encryption
├── calendar/         local device calendar sync
├── data/             Room entities, DAOs, migrations, DataStore, demo data
├── domain/           pure Kotlin, no Android imports — where the tested logic lives
├── notifications/    channels, exact alarms, boot rescheduling
└── ui/               one package per feature, screen + ViewModel
```

Anything with a rule worth arguing about (week parity, occurrences, group generation, seat
adjacency, backup validation) belongs in `domain/` with unit tests, not in a ViewModel.

## Conventions

Commits follow Conventional Commits. Work on a branch, then merge with `--no-ff`. Commit
trailers used in this project:

```
Co-Authored-By: Claude <noreply@anthropic.com>
```

## Current state

Feature-complete for v1 and merged to `main`. Open work is listed under "Roadmap / TODO" in
README.md — the main one is importing a Pronote `.ics` timetable, which is blocked on getting
a real export file from the user.
