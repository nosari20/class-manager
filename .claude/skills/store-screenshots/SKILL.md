---
name: store-screenshots
description: Use to regenerate the Play Store screenshots and graphics for MaClasse after a UI change. Covers seeding presentable demo data, cleaning the status bar, capturing each screen, and redrawing the icon and feature graphic.
---

# Regenerating the store assets

Assets live in `docs/playstore/`. Screenshots are French only — that is the primary listing
language.

## 1. Target the emulator

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
```

A physical phone is sometimes attached. Without the serial, adb refuses to act, and an install
could land on a real device.

## 2. Seed presentable data

Real data must never appear in a store listing. Wipe and reseed:

```powershell
adb shell pm clear edu.fnosari.classmanager
adb shell cmd locale set-app-locales edu.fnosari.classmanager --locales fr-FR
adb shell am start -n edu.fnosari.classmanager/.MainActivity
```

Then in the app: Classes → Réglages → *Créer des données de démo*.

The demo classes and rooms are suffixed " (démo)", which looks unfinished in a listing. Strip
it directly in the database. `sqlite3` is not on the emulator but ships in the host's
`platform-tools`:

```bash
export ANDROID_SERIAL=emulator-5554
export MSYS_NO_PATHCONV=1          # Git Bash rewrites /data/... into a Windows path otherwise
PKG=edu.fnosari.classmanager
adb shell am force-stop $PKG
adb exec-out run-as $PKG cat databases/classmanager.db > cm.db
sqlite3 cm.db "UPDATE school_class SET name = substr(name,1,length(name)-7) WHERE name LIKE '% (d%mo)';
               UPDATE room         SET name = substr(name,1,length(name)-7) WHERE name LIKE '% (d%mo)';
               UPDATE seating_plan SET name = 'Plan de septembre';
               PRAGMA wal_checkpoint(TRUNCATE);"
adb push cm.db /data/local/tmp/cm.db
adb shell run-as $PKG cp /data/local/tmp/cm.db databases/classmanager.db
adb shell run-as $PKG rm -f databases/classmanager.db-wal databases/classmanager.db-shm
adb shell rm -f /data/local/tmp/cm.db
```

Match the " (démo)" suffix with `LIKE '% (d%mo)'` rather than typing the accented character —
it survives shell encoding. The suffix is 7 characters long, hence `length(name)-7`.

## 3. Clean the status bar

```powershell
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

Exit it when finished: `adb shell am broadcast -a com.android.systemui.demo -e command exit`.

## 4. Capture

Nine screens, in this order, into `docs/playstore/screenshots/phone/fr/`:

| File | Screen | How to get there |
|---|---|---|
| `01-today.png` | Today | app launch |
| `02-timetable-week.png` | all classes, one week | calendar icon, top right of Today |
| `03-classes.png` | class list | Classes tab |
| `04-class-students.png` | roster | open 6eA |
| `05-class-seating.png` | seating plan | *Plan* tab |
| `06-picker.png` | random picker **after a draw** | dice icon, then *Tirer* |
| `07-groups.png` | generated groups | people icon, then *Générer* |
| `08-student.png` | student page | tap a student in the roster |
| `09-room-editor.png` | room editor, U-shaped room | Réglages → Salles → Salle Arts |

```powershell
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png docs\playstore\screenshots\phone\fr\01-today.png
```

Re-dump the view hierarchy after every navigation before computing the next tap, and **read
each screenshot** — the picker in particular must show a drawn name, not the empty prompt, and
it is easy to capture a stray dialog without noticing.

## 5. Graphics

`docs/playstore/graphics/icon-512.png` (512×512) and `feature-graphic-1024x500.png` are drawn
with PowerShell + `System.Drawing`, using the launcher icon's geometry from
`app/src/main/res/drawable/ic_launcher_*.xml`: background `#16866F`, stripes `#0F6E5B`, white
"M" monogram stroked with round caps at 9/108 of the canvas width. Point arrays must be cast
(`[System.Drawing.PointF[]]$pts`) or `DrawLines` rejects them.

Only redraw these if the launcher icon changes; keep the two consistent.

## 6. Check the text still matches

If the UI gained or lost a feature, update `docs/playstore/listing/fr-FR/full_description.txt`
(and the `en-US` one). Limits: title 30 characters, short description 80, full description
4000.
