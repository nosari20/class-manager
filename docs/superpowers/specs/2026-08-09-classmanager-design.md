# MaClasse (ClassManager) — Design Spec

**Date:** 2026-08-09
**Status:** Approved by user

## Branding

- App name: **MaClasse** (echoes French public-edu naming: "MonLycée.net", "Ma classe à la maison"). Package id stays `edu.fnosari.classmanager`.
- Launcher icon: adaptive vector — green #16866F background with darker diagonal-stripe corner (in-app stripe motif), white rounded "M" monogram foreground; monochrome layer for themed icons.
- Local device calendar (sync target) is also named "MaClasse".

## Purpose

Android app for a teacher to manage classes: student rosters (with photos), CSV import from Pronote/ENT exports, fair random student picker, per-student notes/reminders with notifications, and manual backup/restore (file-based, works with Google Drive via system picker).

Offline-only. No server, no account. French + English UI (French default via system locale).

## Constraints

- minSdk 29 (Android 10+), targetSdk 37, Kotlin + Jetpack Compose (existing scaffold).
- Single Gradle module, MVVM.
- Storage: Room (SQLite) + photo files in app-private storage.

## Architecture

- **UI:** Jetpack Compose, Material3, single Activity, Navigation Compose.
- **State:** ViewModel per screen, Room Flow → StateFlow.
- **Persistence:** Room database `classmanager.db`; photos in `filesDir/photos/`, DB stores relative paths.
- **Scheduling:** AlarmManager exact alarms + BootReceiver reschedule.
- **DI:** manual (simple AppContainer); no Hilt — app is small.

## Data model (Room entities)

| Entity | Fields |
|---|---|
| SchoolClass | id, name (e.g. "5eB"), level (e.g. "5e"), createdAt |
| TimetableSlot | id, classId, dayOfWeek, startTime, endTime, weekParity (BOTH / A / B) |
| Student | id, classId, lastName, firstName, photoPath?, pickedInCurrentCycle, absentToday, absentTodayDate |
| Note | id, studentId, text, createdAt |
| CustomField | id, studentId, key, value |
| Reminder | id, studentId, text, type (NEXT_LESSON / MORNING_DIGEST / FIXED_DATETIME), dueAt, done, createdAt |
| SeparationConstraint | id, classId, studentAId, studentBId |
| Grouping | id, classId, name, createdAt |
| Room | id, name |
| Desk | id, roomId, x, y (normalized 0..1), seats (1 or 2) |
| SeatingPlan | id, classId, roomId, name, createdAt |
| SeatAssignment | planId, deskId, seatIndex, studentId |
| GroupingGroup | id, groupingId, index |
| GroupingMember | groupId, studentId |
| AppSettings (DataStore, not Room) | weekAReferenceDate, digestTime (default 07:00), lastCsvMapping |

Cascade deletes: class → students → notes/fields/reminders. Deleting entities cancels their alarms.

## Features

### Classes & students (CRUD)

- Home: class list cards (name, level, student count). Create/edit/delete class (delete = confirmation dialog).
- Class detail: student grid (photo thumbnail + name), add/edit/delete student.
- Timetable editor per class: weekly slots (day, start, end, parity BOTH/A/B).
- Student photo: Android Photo Picker or camera capture; downscaled to max 512px JPEG before saving (keeps backups small).

### A/B week parity

- Settings stores a reference date declared as week A.
- Parity of any date = ISO week number offset from reference, mod 2.
- "Next lesson" computation skips slots whose parity doesn't match the target week.

### CSV import (Pronote/ENT)

- SAF open-document picker (`text/*`, `*/*` fallback).
- Parser: auto-detects separator (`;` or `,`), handles quoted fields, tries UTF-8 then Windows-1252 (French Excel default).
- Mapping screen: dropdowns to select Nom / Prénom columns (pre-guessed from common header names), live preview of first parsed rows, inputs for class name + level, confirm → creates class + students.
- Malformed rows shown as skipped in preview; import never crashes.
- Last used mapping remembered in settings.

### Random picker (no-repeat cycle)

- Per class. Picks uniformly among students with `pickedInCurrentCycle = false` and not absent today.
- When all eligible students picked → cycle auto-resets (with visual cue). Manual reset button.
- Absence toggles on picker screen; `absentToday` auto-clears when date changes (checked against absentTodayDate).
- Big-card reveal animation with student photo/name.

### Student detail page

- Photo, name, class.
- Custom fields: free key-value list (allergies, PAP/PAI, contacts…), add/edit/delete.
- Notes: timestamped free-text timeline, newest first, add/edit/delete.
- Reminders: active + done list, create from here.

### Reminders & notifications

Three types:
1. **NEXT_LESSON** — at creation, compute next timetable slot of the student's class (respecting A/B parity); store concrete `dueAt`; notification fires 5 min before slot start. If class has no timetable slots, prompt to add one or fall back to manual date/time.
2. **FIXED_DATETIME** — user picks date+time.
3. **MORNING_DIGEST** — date-only; included in the single daily digest notification at digest time (default 07:00) listing all items due today.

Mechanics:
- `AlarmManager.setExactAndAllowWhileIdle`; request `SCHEDULE_EXACT_ALARM` (Android 12+), degrade to inexact if denied.
- `POST_NOTIFICATIONS` runtime permission (Android 13+), requested on first reminder creation.
- `BootReceiver` (RECEIVE_BOOT_COMPLETED) reschedules pending alarms.
- Notification tap deep-links to StudentDetailScreen; "Done" action button on notification.
- Notification channels: "Reminders" and "Daily digest".

### Group generator with constraints

- Per class. Persistent **separation constraints**: pairs of students who must never share a group ("X ✕ Y"), managed in a constraints editor (add via two-student picker, delete). Reused across all generations.
- Split mode toggle: "groups of N" (remainder spread across groups) or "N groups" (sizes balanced). Option to exclude students absent today.
- Algorithm: shuffle roster, greedily assign each student to the smallest group with no separation violation, backtrack on dead ends, retry with a fresh shuffle (max ~100 attempts). Class sizes ≤ 40 → effectively instant. If infeasible, report the clashing constraints instead of failing silently.
- Result: group cards; actions: reshuffle, manual edit (move student between groups — constraint-violating moves allowed but flagged red, teacher decides), save with a name (e.g. "TP chimie 12/09").
- Saved groupings listed per class: view, rename, delete. Regenerate = new grouping.

### Rooms & seating plans (plan de classe)

- Rooms are app-level and shared between classes ("Salle 102"). Room editor: free-placement canvas with board marker at top; tap empty space adds a desk (1-seat or 2-seat, chosen by a toggle), drag moves it, long-press opens a menu (switch 1↔2 seats, delete). Desk positions stored normalized (0..1); 2-seat tables render double-width with a divider and each seat is assigned independently. Shrinking a table to 1 seat unassigns its second seat.
- Seating plans: multiple named plans per class, each tied to a room (like groupings). Create (name + room), open, rename, delete.
- Placement is manual: tap a free desk → student picker; tap an occupied desk → remove or replace. Unassigned students shown as a chip row; assignments persist immediately.
- Warning only: if two students under a separation constraint sit adjacent (distance below ~1.5 desk widths), their desks get an error-colored outline.
- Cascades: deleting a room deletes its desks/plans/assignments; deleting a class deletes its plans; deleting a student clears their seat.

### Backup / restore

- **Backup:** Room checkpoint (`wal_checkpoint(TRUNCATE)`) → zip `classmanager.db` + `photos/` + `manifest.json` (schema version, app version, date) + `settings.json` (week A reference, digest time, CSV column mapping) → SAF create-document (`application/zip`), filename `classmanager-backup-YYYY-MM-DD.zip`. User picks Google Drive or any destination. The DB file carries every Room table (classes, students, notes, fields, reminders, constraints, groupings, rooms, desks, seating plans, assignments, slot cancellations, one-off slots).
- **Restore:** SAF open-document → validate zip structure + manifest before touching anything → explicit confirmation dialog ("replaces all current data") → close DB, swap files, reopen, reschedule all alarms.
- Any failure during validation or extraction leaves current data untouched (extract to temp dir, atomic swap).
- Manifest schema version checked; newer-than-app versions rejected with message.

## Screens

| Screen | Content |
|---|---|
| ClassListScreen | Class cards; FAB menu: new class / import CSV; overflow: settings |
| ClassDetailScreen | Tabs: students (grid), timetable, seating; buttons → picker, groups, seating plans. Route takes an optional `roomId`: when set the screen opens on the seating tab for that room |
| GroupGeneratorScreen | Constraints editor, split toggle, generate/reshuffle, manual edit, save; past groupings list |
| RandomPickerScreen | Reveal animation, pick-again, cycle progress, reset, absence toggles |
| StudentDetailScreen | Photo, fields, notes timeline, reminders |
| CsvImportScreen | File → mapping dropdowns → preview → class name/level → confirm |
| SettingsScreen | Language, appearance, digest time, week A reference, rooms, backup, restore, demo data, calendar sync |
| GlobalTimetableScreen | Read-only week view of ALL classes' occurrences (cancellations + one-offs applied), ‹ › week browser, class-colored cards with room pill; card opens the class. Entry: calendar icon in Today tab top bar |

### Checklists (class page → *Listes*)

Collecting one thing from a whole class — a signed permission slip, a trip payment, a returned
book. A checklist belongs to a class and has a title and an optional due date; a
`checklist_entry` row means that student has handed it in, so no row is the natural "still
missing" state and a student who leaves takes their tick with them via the foreign key.

The tab lists each checklist with a progress bar and either "*n* manquants" in the error colour
or "Au complet". Opening one shows the class with checkboxes, a running count, and *Tout
cocher* / *Tout décocher* for the common cases (everyone paid; start a new round). Tables:
`checklist`, `checklist_entry` — database v7, `BackupManager.SCHEMA_VERSION` 7.

### Seating tab on the class page

Tapping a course in the Today tab opens the full class page on its seating tab, showing the layout of that course's room — the same screen reached from the Classes list, so students, timetable, picker and groups stay one tap away. Opened from the Classes list (no room in the route), the tab falls back to the room of the class's current course, or its next one. With no plan for that room it offers the seating-plans screen. The plan body (`SeatingPlanBody`) is shared with the standalone seating screen, so seats stay editable in the tab.

### Language and appearance (Settings)

Both stored in DataStore (so they travel in `settings.json` backups) and cached in `AppContainer` at startup, because the locale must be applied in `Activity.attachBaseContext`, before the first frame.

- **Language** — System default / Français / English. On API 33+ the framework owns it (`LocaleManager.applicationLocales`), so the choice also appears in Android's per-app language settings and the activity is recreated by the system; `res/xml/locales_config.xml` + `android:localeConfig` declare the supported set. Below API 33 `AppLocale.wrap` overrides the resource configuration and the activity recreates itself. The picker reads back through the framework on 33+ so an OS-side change is reflected.
- **Appearance** — System default / Light / Dark, resolved by the pure `isDarkTheme(pref, systemDark)`; applies instantly, no recreate.
- **App colour** — nine presets (`Palette.PRESETS`, stored as ARGB) recolour `colorScheme.primary` and everything derived from it: top bars, buttons, FABs, progress bars, and the status bar. `Palette` is plain ARGB maths so the same functions serve Compose and the system-bar setup in `MainActivity`. The dark theme lightens the seed (`primaryFor`), because a deep colour swallows the top bar; the status-bar icons then follow the *bar's* luminance rather than the theme, since a pale bar in dark mode still needs dark icons. `isLight` uses a 0.28 threshold — above the WCAG crossover (0.179) so all presets keep white text in light mode, below the lightened variants so those flip to dark.

### Demo data (Settings)

"Créer des données de démo" inserts, on top of existing data (never deletes): 3 classes ("6eA/5eD/4eC (démo)") × 12 students with multicultural names, 2 rooms (grid of 2-seat tables + U-shaped room with rotated side tables), a filled seating plan, timetable slots (one per class on the current day so Today shows content), a separation constraint, notes, a custom field, and 2 reminders due today. `data/DemoData.kt`.

## Error handling

- CSV: skipped-row feedback, never crashes on malformed input.
- Restore: validation before mutation; atomic swap; clear error messages.
- Alarms: permission denials degrade gracefully (inexact alarms, or in-app-only reminders if notifications denied).
- Photo capture/pick failures: student saved without photo, retry possible.

## Testing

- **Unit:** CSV parser (separators, encodings, quotes, malformed rows), A/B parity + next-slot computation (incl. year boundaries), picker cycle logic, backup manifest validation, group generation (constraints respected, balanced sizes, infeasible detection).
- **Instrumented:** Room DAO tests, migration tests (once v2 exists).
- **Manual:** notification timing, boot reschedule, SAF flows, Drive round-trip.

## Out of scope (v1)

- Automatic/scheduled cloud backup, Google sign-in.
- Attendance tracking / participation stats.
- Grades, seating charts, multi-teacher sync.
- Recurring reminders.
