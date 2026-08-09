---
name: db-migration
description: Use when adding, removing or changing a Room entity, column, primary key or foreign key in MaClasse. Covers the full checklist — version bump, migration, schema export, backup SCHEMA_VERSION, tests — so a change does not silently break restoring older backups.
---

# Changing the database

A schema change touches five places. Missing any one of them fails at runtime, usually on a
user's device during a restore, so work through all of them.

## 1. Edit the entity

`app/src/main/java/edu/fnosari/classmanager/data/Entities.kt`.

New columns need a default that SQLite can apply to existing rows. For anything Room will
auto-migrate, give the column a `@ColumnInfo(defaultValue = "…")` as well as a Kotlin default
— Room needs the SQL-level default to write the `ALTER TABLE`:

```kotlin
@ColumnInfo(defaultValue = "0") val vertical: Boolean = false
```

## 2. Bump the version and add the migration

`data/AppDatabase.kt`. Prefer an auto-migration; it is one line and cannot drift from the
entity:

```kotlin
@Database(version = 7, autoMigrations = [AutoMigration(from = 6, to = 7)], exportSchema = true)
```

Room **cannot** auto-migrate a changed primary key, a new foreign key, or a column rename it
cannot infer. Those need a hand-written `Migration` that creates the new table, copies the
data, drops the old one and renames — follow `MIGRATION_2_3` and `MIGRATION_4_5` in that file,
which do exactly this. Register it in the `databaseBuilder`.

Note that the entity named `Room` collides with `androidx.room.Room`, so the builder call is
fully qualified in this file. Keep it that way.

## 3. Export the schema

Schemas are committed to `app/schemas/`. Building regenerates them:

```powershell
.\gradlew.bat :app:assembleDebug
```

Commit the new `<version>.json` alongside the code. A missing schema file makes the migration
untestable and breaks the next auto-migration.

## 4. Bump the backup schema version

`backup/BackupManager.kt`:

```kotlin
const val SCHEMA_VERSION = 7   // must equal the @Database version
```

This is the step that gets forgotten. `BackupManager.validate` rejects a backup whose
`schemaVersion` is *newer* than the app. If the constant lags behind the database version, the
app writes backups claiming an old version, and a future release will accept them and then
restore a database that does not match its own schema.

## 5. Test it

- Add or extend a case in `app/src/test/java/…/data/DaoTest.kt` covering the new column or
  table through the DAO.
- Run the suite: `.\gradlew.bat :app:testDebugUnitTest`
- Verify a migration from real data, not just a fresh install: install the **previous** build
  on the emulator, create some data, then install the new build over it and confirm the app
  opens and the data survived. A migration that only ever runs against an empty database has
  not been tested.
- If the change touches anything included in a backup, take a backup with the old build and
  restore it with the new one.

## Also check

Does the new data need to appear in `settings.json` or the backup zip (`BackupManager.writeZip`
and the restore path)? Does `DemoData.seed` need to populate it so the demo stays
representative?
