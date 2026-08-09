# Contributing to MaClasse

Thanks for taking the time. This is a small project run by one teacher-developer, so the
process is deliberately light.

## Reporting a bug

Open an issue with the Android version, the device (or emulator) it happened on, what you
did, what you expected, and what happened instead. If the app crashed, `adb logcat` output
around the crash helps enormously.

**Do not attach real student data.** Screenshots and backups routinely contain names. Use
the demo data generator (Settings → *Create demo data*) to reproduce the problem with
fictional pupils instead.

Security problems go to [SECURITY.md](SECURITY.md), not to the public tracker.

## Suggesting a feature

Say what you are trying to do in your teaching day, not only the feature you imagined. The
underlying need usually suggests a simpler design than the requested button.

The app is deliberately offline and account-free. Proposals that require a server, an
account, or network access will be weighed against that, and are likely to be declined
unless the benefit is large and the data stays under the teacher's control.

## Working on the code

```bash
./gradlew testDebugUnitTest      # must pass
./gradlew lintDebug              # must report 0 errors
./gradlew installDebug           # try it on a device or emulator
```

Please run all three before opening a pull request, and say in the PR what you verified on
a real device or emulator — most of this app's behaviour (alarms, pickers, drag gestures,
notifications) cannot be judged from unit tests alone.

### Conventions

- **Domain logic goes in `domain/`**, as plain Kotlin with no Android imports, and comes with
  unit tests. Anything with a rule worth arguing about — parity, occurrences, group
  generation, seat adjacency — belongs there rather than in a ViewModel.
- **One feature package per screen** under `ui/`, each with its screen and ViewModel.
- **Both string files change together.** `values/strings.xml` and `values-fr/strings.xml` are
  kept key-for-key identical; a PR that adds a key to one and not the other will be asked to
  fix it. No user-visible text hard-coded in Kotlin.
- **Database changes** need a new version, a migration, an updated schema JSON in
  `app/schemas/`, and a bump of `BackupManager.SCHEMA_VERSION`. Forgetting the last one makes
  older backups restore into a database they do not match.
- Follow the surrounding style: 4-space indent, trailing commas in multi-line argument lists,
  comments only where the code cannot say it itself.

### Commits

[Conventional Commits](https://www.conventionalcommits.org): `feat:`, `fix:`, `docs:`,
`refactor:`, `test:`, `chore:`. Write the body for someone reading it in a year — what the
change does and why, not how you arrived at it.

## Licensing of contributions

The project is GPL-3.0-or-later. By submitting a contribution you agree it is licensed under
those terms.
