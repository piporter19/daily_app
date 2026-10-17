# Locking the database (do this once, about 10 minutes)

## What was wrong
The old page checked your password **in the browser** (`"what's up"` was written in the page source) and then talked to Firestore with no sign-in at all. Anyone who opened "view source" could read the password, or skip it and call the database directly, because the Firebase key in the page is public by design and the rules were open.

## What changed
1. **Real sign-in.** The lock screen still takes just your password, but it is now checked by **Firebase Authentication**. The password never appears in the app; Google checks it. Caveat: short passwords like `soham` can be guessed. Firebase slows repeated guesses, but for real strength pick longer passwords in the console (the app adds the same tail, so create them as `<new password>::pip-tracker`) and type the new password on the lock screen.
2. **Rules on the server.** `firestore.rules` says: only a signed-in account may touch data, and only its own dashboard (`my_dashboard` or `soham_dashboard`, plus the photo docs that start with that name). Everything else is refused, even for someone holding the key.
3. **Owner table.** Which account owns which dashboard is stored in a small `owners` collection that you create by hand. The app can read its own row but can never write one, so nobody can promote themselves.
4. **The key stays in the page** and that is fine. A Firebase web key is just a project address, not a secret. Without a signed-in account the rules refuse everything.
5. **Your old data does not move.** Same document names, so nothing is migrated.

## Steps (in this order)
Open https://console.firebase.google.com and pick the project `daily-tracker-bbee6`.

### 1. Turn on sign-in
Build > **Authentication** > Get started > **Email/Password** > Enable > Save.

### 2. Create the two accounts
The lock screen stays password-only: you type `what's up` or `soham`, nothing else. Behind it are two fixed accounts. Authentication > Users > **Add user**:

| Who | Email | Password |
|---|---|---|
| You | `me@pip-tracker.app` | `what's up::pip-tracker` |
| Soham | `soham@pip-tracker.app` | `soham::pip-tracker` |

(The `::pip-tracker` tail is added by the app, because Firebase needs 6+ characters and `soham` has 5. The emails are just labels.) Copy each user's **User UID**.

### 3. Say who owns what
Build > **Firestore Database** > Start collection (or add): collection ID `owners`.
- Document ID = your **UID**, field `dash` (string) = `my_dashboard`
- Document ID = Soham's **UID**, field `dash` (string) = `soham_dashboard`

### 4. Publish the rules
Firestore Database > **Rules** tab > replace everything with the contents of `firestore.rules` > **Publish**.

### 5. Install the new build
The new app signs in; the old one will stop working the moment the rules go live (that is the point). Build the APK (README), install, sign in.

### 6. Tidy up
- Turn off the old check-in job: GitHub repo > Actions > the pip-checkins workflow > ⋯ > **Disable workflow**, and delete its secrets. It read your data without signing in, so it stops working after step 4 anyway, and the app now sends notifications itself.
- If you host the web version too, add its domain under Authentication > Settings > **Authorized domains**.
- Optional: Google Cloud Console > APIs & Services > Credentials > the browser key > restrict to your domains and the Identity Toolkit and Firestore APIs.

## Test it
Signed out, from a browser console on any page, a read of `tracker_data/my_dashboard` should now fail with "Missing or insufficient permissions". In the app, signing in with a wrong password should show an error, and the right one opens your day.
