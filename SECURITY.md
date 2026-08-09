# Security Policy

## Supported versions

The latest release is the only supported version.

## Reporting a vulnerability

Email **nosari20@gmail.com** with "MaClasse security" in the subject. Please do not open a
public issue for a security problem.

Include what an attacker can do, the steps to reproduce it, and the Android version you saw
it on. You will get an acknowledgement within a week. Since this is a spare-time project run
by one person, a fix may take longer than that — you will be told where it stands.

**Never include real student data** in a report. Reproduce with the app's demo data
(Settings → *Create demo data*).

## What is in scope

This app holds pupil names, photos, notes and reminders on a teacher's phone. The
interesting attack surface is small but real:

- Backup files: the encrypted format (AES-256-GCM, PBKDF2-SHA256 200 000 iterations,
  `CMENC1` header), and anything that would let an unencrypted backup expose data
  unexpectedly
- Restore: a malicious backup file being able to write outside the app's directories, or to
  execute anything
- CSV and file import: crashes or worse from malformed input
- Exported components: anything reachable from another app on the device
- Notification content leaking pupil names on a locked screen beyond what the user chose

Out of scope: an attacker who already has an unlocked phone, or physical access to a rooted
device — the app relies on Android's app sandbox and the device lock for at-rest protection
and does not claim to defend against either.
