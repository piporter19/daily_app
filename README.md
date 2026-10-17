# Pip Tracker (Android)

Your tracker, wrapped with Capacitor, plus a native focus blocker.

## Build the APK (no Android Studio needed)
1. Push this folder to a GitHub repo (branch `main`).
2. Open the **Actions** tab, run **Build Android APK** (it also runs on every push).
3. Download the `pip-tracker-debug-apk` artifact, unzip it, and install `app-debug.apk` on your phone
   (allow "install unknown apps" for your browser or Files app).

## Before first use: lock the database
Do **SECURITY-SETUP.md** (about 10 minutes). The app now signs in with email and password, and the Firestore rules in `firestore.rules` only allow your own account.

## Check-ins
Notifications are sent by the app itself (alarms on the phone, with buttons that log without opening the app). ntfy and the GitHub Actions check-in job are no longer needed. Allow notifications and exact alarms from the Check-ins sheet.

## First run
Open the lock button, then **Focus session**. It walks you through:
- **Accessibility**: Settings > Accessibility > Pip Tracker > On (required)
- Display over other apps, and unrestricted battery (recommended; on Xiaomi/Samsung/OnePlus this keeps the blocker alive)

## Editing the tracker
The whole web app is `www/index.html`. Edit it, push, and the workflow rebuilds the APK.

## Where things live
- `android/app/src/main/java/com/pip/tracker/`
  - `FocusAccessibilityService`: notices which app is in front and covers disallowed ones
  - `BlockActivity`: the "stay with it" screen, with the wait before you can leave early
  - `FocusBlockerPlugin`: the bridge the web page calls
  - `FocusPrefs`: session state
  - `Nudge`, `NudgePlugin`, `NudgeReceiver`, `NudgeActionReceiver`, `BootReceiver`: scheduled check-in notifications
