# What changed in this build

MUST DO ONCE in Firebase console (otherwise "permission denied" stays):
1. Realtime Database > Rules > paste the whole `database.rules.json` > Publish.
2. Authentication > Sign-in method > Email/Password must be ON.
3. Log in once in the app with meshaidalertsos@gmail.com / meshaid@1 (the account is created automatically on first use). That opens the Control Center.

Fixes
- Admin: real Firebase login for the fixed email only; opens Control Center on any phone/laptop. Relay decryption key is saved (password-protected) in `centerKeyBackup`, so a new device can read encrypted SOS packets.
- Only the admin email can write `roles` / `config/center`; nobody else can become a control center.
- Blood "permission denied": database rules lacked `bloodResponses`, `incidentReports`, `sightings`, `stats`, `medicines` -> added.
- Medicine: stored per account (user_id), other accounts' medicines/alarms never show or ring; backed up in Firebase `medicines/{uid}`, restored on login/other phone. Logout/delete wipes this account's local data.
- Sync: account deleted/disabled in Firebase -> app signs out (checked every minute, on every screen open, within ~4 s of the check). Deleted from the app -> removes users, phoneIndex, directory, donors (RTDB + Firestore), medicines. Profile removed from DB is restored from the phone.
- Offline map: while online the app saves real road routes to the nearest hospitals/police/fire within 50 km; offline it shows these (starting point within 25 km), otherwise falls back to the old estimate.

## Update 2
- Crash catcher: if the app crashes, the next screen shows the exact error in a dialog (select + screenshot it).
- Background jobs can no longer kill the app; a crash loop signs out so the login screen appears.
- Medicine screen shows "Cloud backup OK / FAILED: reason". Medicines saved by very old versions (no owner) are no longer shown to any account.
- Offline routes now cover hospital, police, fire, blood bank and shelter: everything within 20 km (max 10 per kind) and at least the 4 nearest within 50 km. Directory is downloaded for the 50 km area first.
- Assistant chat is stored per account; logout clears that account's chat; old shared chat is hidden.
