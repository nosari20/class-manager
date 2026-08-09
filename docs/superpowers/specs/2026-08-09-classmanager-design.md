# ClassManager — Design Spec

**Date:** 2026-08-09
**Status:** Approved by user

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

### Backup / restore

- **Backup:** Room checkpoint (`wal_checkpoint(TRUNCATE)`) → zip `classmanager.db` + `photos/` + `manifest.json` (schema version, app version, date) → SAF create-document (`application/zip`), filename `classmanager-backup-YYYY-MM-DD.zip`. User picks Google Drive or any destination.
- **Restore:** SAF open-document → validate zip structure + manifest before touching anything → explicit confirmation dialog ("replaces all current data") → close DB, swap files, reopen, reschedule all alarms.
- Any failure during validation or extraction leaves current data untouched (extract to temp dir, atomic swap).
- Manifest schema version checked; newer-than-app versions rejected with message.

## Screens

| Screen | Content |
|---|---|
| ClassListScreen | Class cards; FAB menu: new class / import CSV; overflow: settings |
| ClassDetailScreen | Student grid; sections/tabs: students, timetable; button → picker |
| RandomPickerScreen | Reveal animation, pick-again, cycle progress, reset, absence toggles |
| StudentDetailScreen | Photo, fields, notes timeline, reminders |
| CsvImportScreen | File → mapping dropdowns → preview → class name/level → confirm |
| SettingsScreen | Digest time, week A reference, backup, restore, about |

## Error handling

- CSV: skipped-row feedback, never crashes on malformed input.
- Restore: validation before mutation; atomic swap; clear error messages.
- Alarms: permission denials degrade gracefully (inexact alarms, or in-app-only reminders if notifications denied).
- Photo capture/pick failures: student saved without photo, retry possible.

## Testing

- **Unit:** CSV parser (separators, encodings, quotes, malformed rows), A/B parity + next-slot computation (incl. year boundaries), picker cycle logic, backup manifest validation.
- **Instrumented:** Room DAO tests, migration tests (once v2 exists).
- **Manual:** notification timing, boot reschedule, SAF flows, Drive round-trip.

## Out of scope (v1)

- Automatic/scheduled cloud backup, Google sign-in.
- Attendance tracking / participation stats.
- Grades, seating charts, multi-teacher sync.
- Recurring reminders.
