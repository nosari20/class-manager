# Play Store publishing pack

Everything needed for a Google Play listing of **MaClasse**. Assets here are generated from
the app itself — regenerate them rather than editing by hand when the UI changes.

```
docs/playstore/
├── graphics/
│   ├── icon-512.png                   512×512 app icon (required)
│   └── feature-graphic-1024x500.png   1024×500 feature graphic (required)
├── screenshots/phone/fr/              9 phone screenshots, 1280×2856
└── listing/
    ├── fr-FR/  title, short & full description (primary listing)
    └── en-US/  same, English
```

## Before you upload

**Application ID — decided, do not change.** The app ships as `edu.fnosari.classmanager`.
It does not match the app's name, and that is fine: the ID is never shown to users, only the
store listing title is. It is now permanent — after the first release a different ID would be
a different app on Play, with no upgrade path for anyone who installed this one.

**1. There is no release signing config yet.** `./gradlew bundleRelease` will not produce an
uploadable artifact until you add one. Create a keystore, keep it somewhere you will still
have in five years, and never commit it:

```bash
keytool -genkeypair -v -keystore maclasse-release.jks -keyalg RSA -keysize 4096 \
        -validity 10000 -alias maclasse
```

Reference it from `app/build.gradle.kts` through a `keystore.properties` file that stays out
of git. Losing this key means you can never update the app again — enrol in Play App Signing
so Google keeps a recoverable copy.

**2. Release builds currently skip optimisation** (`optimization { enable = false }` in
`app/build.gradle.kts`). Turn it on for release and test the resulting build, since R8 can
break reflection-based code — Room and Compose are fine, but verify a restore and a
notification on the release build before shipping.

## Store listing fields

| Field | Value |
|---|---|
| App name | MaClasse |
| Default language | French (France) — `fr-FR` |
| App or game | App |
| Category | Education |
| Tags | Teaching tools, Productivity |
| Contact email | nosari20@gmail.com |
| Website | https://github.com/nosari20/class-manager |
| Privacy policy URL | https://github.com/nosari20/class-manager/blob/main/PRIVACY.md |
| Free or paid | Free |
| Contains ads | No |
| In-app purchases | No |

Screenshots: Play accepts between 2 and 8 phone screenshots, and the pack contains 9. Suggested
selection and order (the first two are what most people ever look at):

1. `01-today.png` — the day at a glance
2. `05-class-seating.png` — the seating plan, the most distinctive feature
3. `04-class-students.png` — a class roster
4. `06-picker.png` — the random picker
5. `07-groups.png` — group generation with constraints
6. `02-timetable-week.png` — the week across all classes
7. `09-room-editor.png` — drawing a room
8. `08-student.png` — a student page

Leave out `03-classes.png` unless you drop one of the above.

## Data safety form

The honest answers are short, because the app collects nothing:

- **Does your app collect or share any of the required user data types?** → **No.**
- **Is all of the user data encrypted in transit?** → Not applicable, no data is transmitted.
- **Do you provide a way for users to request that their data is deleted?** → Yes —
  uninstalling removes everything; data is only on the device.

If the form pushes back because the app writes files, note that a backup is written **by the
user, to a location the user chooses, through the system file picker**. That is not
collection or sharing by the app: nothing is sent to the developer or to any third party.
The app has no `INTERNET` permission, which is the simplest proof.

## Content rating questionnaire

Category: *Utility, Productivity, Communication or Other*. Every content question (violence,
sexuality, language, controlled substances, gambling, user-generated content sharing,
location sharing, personal information sharing) is **No**. Expected outcome: PEGI 3 /
ESRB Everyone / rated for all ages.

## Target audience and children

Target age group: **18 and over**. The app is a tool for teachers, not for pupils, so it is
not designed for children and should not be enrolled in Teacher Approved / Designed for
Families. Say so plainly on the target-audience form: the pupils appear only as data entered
by an adult professional.

## Permissions that need a declaration

`SCHEDULE_EXACT_ALARM` triggers a review prompt. The justification: reminders are the app's
core purpose and a teacher sets an explicit time for each one — a reminder delivered an hour
late, after the lesson it was about, has failed. The app already degrades to an inexact alarm
when the permission is unavailable, rather than refusing to work.

There is no location, contacts, microphone, or network permission to declare.

## Regenerating the assets

**Screenshots** — capture on a phone-shaped emulator with the demo data:

1. Wipe and reseed so the content is fictional and consistent:
   `adb shell pm clear edu.fnosari.classmanager`, launch, then Réglages → *Créer des données
   de démo*.
2. Strip the " (démo)" suffixes so the listing looks like real usage — pull the database with
   `adb exec-out run-as edu.fnosari.classmanager cat databases/classmanager.db`, run
   `UPDATE school_class SET name = substr(name,1,length(name)-7) WHERE name LIKE '% (d%mo)'`
   (same for `room`), push it back with `run-as … cp`, and delete the `-wal`/`-shm` files.
3. Clean the status bar with SystemUI demo mode before capturing:
   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
   # when finished:
   adb shell am broadcast -a com.android.systemui.demo -e command exit
   ```
4. `adb exec-out screencap -p > 01-today.png` for each screen.

If a physical phone is also plugged in, set `ANDROID_SERIAL` first — otherwise adb refuses to
choose, and an `install` could land on the wrong device.

**Graphics** — `icon-512.png` and `feature-graphic-1024x500.png` are drawn from the same
geometry as the launcher icon (`app/src/main/res/drawable/ic_launcher_*.xml`): background
`#16866F`, stripe `#0F6E5B`, white "M" monogram. If the launcher icon changes, redraw these to
match.
