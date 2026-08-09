# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[semantic versioning](https://semver.org/).

## [Unreleased]

### Planned

- Import a timetable from a Pronote `.ics` export: days, hours, rooms and classes in one
  file, with the A/B week pattern inferred rather than typed.

## [1.0.0] — unreleased

First public version. Everything below shipped together.

### Added

- Classes and students, with optional photos taken from the camera or the gallery.
- CSV roster import for Pronote/ENT exports, with encoding and separator detection, column
  mapping and a preview before anything is written.
- Weekly timetable per class with A/B week parity and a room per slot; per-week edits
  (cancel an occurrence, restore it, add a one-off course); a global week view of all
  classes; optional sync into a local "MaClasse" device calendar.
- Today screen: the day's courses on a timeline with the current one highlighted, and the
  reminders due today. Tapping a course opens its class on the seating plan for that room.
- Rooms drawn by placing and dragging desks, with one- or two-seat tables that rotate for
  U-shaped layouts; rooms shared across classes; several named seating plans per class and
  room; separation constraints highlighted when violated.
- Random student picker that does not repeat until the whole class has been drawn, with
  same-day absences excluded.
- Automatic group generation honouring "must not be together" constraints, with infeasible
  constraint sets reported rather than silently ignored; groupings saved by name.
- Per-student notes, custom fields and reminders, delivered as notifications at a fixed
  date and time, in a morning digest, or ahead of the next lesson with that class.
- Backup and restore of the whole database, photos and settings to a single file, optionally
  encrypted with a password (AES-256-GCM, PBKDF2-SHA256, 200 000 iterations).
- Demo data generator, so the app can be explored without entering real pupils.
- French and English, and light/dark appearance, each selectable in the app independently of
  the system setting.
