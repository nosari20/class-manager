# Privacy Policy — MaClasse

*Last updated: 9 August 2026*

MaClasse is an offline app for teachers. **It collects nothing, sends nothing, and has no
servers.** The developer has no access to any data you enter.

## What the app stores, and where

Everything you create — classes, students, photos, notes, custom fields, reminders,
timetables, rooms, seating plans, groups, and your settings — is stored **only in the app's
private storage on your device**. It is not uploaded anywhere.

The app is installed without the `INTERNET` permission, so it is technically incapable of
transmitting your data over a network.

There is no account, no sign-in, no analytics, no advertising, no crash reporting, and no
third-party SDK that collects usage data.

## When data leaves the device

Only when you deliberately make it happen:

- **Backup** — you choose a destination through Android's file picker. If you pick a cloud
  folder such as Google Drive, the file goes to that provider under your own account, and
  their privacy policy applies from that point. You can protect the file with a password,
  which encrypts it with AES-256-GCM (key derived with PBKDF2-SHA256, 200 000 iterations).
  Doing so is strongly recommended, as a backup contains pupil names, photos and notes.
- **Calendar sync** — if you use it, your course times, class names and room names are
  written into a local calendar named "MaClasse" on your device. If that calendar account is
  itself synced to an online service by your phone, those entries follow it.

## Permissions the app requests, and why

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | to show your reminders and the morning digest |
| `SCHEDULE_EXACT_ALARM` | so a reminder arrives at the time you set |
| `RECEIVE_BOOT_COMPLETED` | to restore pending reminders after the phone restarts |
| `READ_CALENDAR`, `WRITE_CALENDAR` | only if you use the optional calendar sync |
| Camera (optional feature) | only if you take a student's photo with the camera |

There is no location, contacts, microphone, or network permission.

## Children's data

The app is for teachers, not pupils, and is not directed at children. But the data you enter
describes pupils, who are often minors. You remain the person responsible for that data
under GDPR and your institution's rules. In practice: use a device lock, encrypt your
backups, and delete data you no longer need.

## Deleting your data

Uninstalling the app deletes everything it stored on the device. Backup files you exported
yourself are not affected — delete those wherever you saved them.

## Changes

Any change to this policy will appear in this file, with the date above updated.

## Contact

nosari20@gmail.com
