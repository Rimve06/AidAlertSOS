# Firebase Console setup (do this once, in order)

Project: `aid-alert-18ac6` · Package: `com.aidalert.sos` · Database: `https://aid-alert-18ac6-default-rtdb.firebaseio.com`

Nothing here is a secret that belongs in Git. **Never commit** `google-services.json`, a service-account key
(`*-firebase-adminsdk-*.json`), a keystore or a database secret. `.gitignore` already lists them.

## 1. Authentication

Console > **Build > Authentication > Get started > Sign-in method**

1. Enable **Email/Password**. Leave "Email link (passwordless)" off.
2. Enable **Phone**.
3. Console > Authentication > **Settings > SMS region policy**: allow **Bangladesh (BD)**. New projects allow no region, so SMS to +880 numbers is refused until you do this.
4. **Real SMS codes need the Blaze (pay-as-you-go) plan** (Firebase Authentication limits page: SMS verification is not available on the free Spark plan). Until you upgrade, add **Phone numbers for testing** (Sign-in method > Phone > "Phone numbers for testing", e.g. `+8801700000000` with code `123456`). Test numbers send no SMS and work on the free plan, so use them for development and the course demo.
5. Phone sign-in needs your app's fingerprints (next step). Google also recommends enabling the **Play Integrity API** for the project (Google Cloud Console > APIs & Services); without it, Firebase falls back to a reCAPTCHA web page.

## 2. Register the Android app and get `google-services.json`

1. Project settings > Your apps > Add app > Android, package `com.aidalert.sos`.
2. Add fingerprints: in Android Studio open the Gradle tab > `app > Tasks > android > signingReport` and copy **SHA-1** and **SHA-256** of the `debug` variant into the app settings. Add the release fingerprints later too.
3. Download `google-services.json` and put it in `app/` (same folder as `app/build.gradle`). It is ignored by Git. Anyone cloning the repo must download their own copy; without it Gradle stops with "File google-services.json is missing".
4. Google Cloud Console > APIs & Services > Credentials: restrict the Android API key to package `com.aidalert.sos` + your SHA-1.

## 3. Realtime Database rules

1. Console > Build > Realtime Database > Rules.
2. Replace everything with the contents of `database.rules.json` from this repo and **Publish**.
   (CLI alternative: `firebase deploy --only database`.)
3. If the database was ever in "test mode" or had `".read": true`, treat its old contents as public. The previous version stored password hashes under `users/{id}/salt|hash`: **delete the whole old `users` node and the old `directory` node** in the Data tab after publishing the rules. New accounts do not create any password data.

What the rules do: nothing is readable or writable without a signed-in user whose ID token has a verified email **and** a verified phone number. Control center powers come only from `roles/{uid} = "CENTER"`, which clients cannot write. Users can write only their own records; incident ids must start with the user's uid; the community board, feedback, reports and audit log are validated.

## 4. Create a control center account

1. In the app, register normally (phone code, email link, password).
2. Console > Authentication > Users: copy that user's **UID**.
3. Console > Realtime Database > Data: add the node `roles` > `<that UID>` with the string value `CENTER`.
4. Log out and log in again using the **Control center** option. A citizen account that is not listed in `roles` is refused there.
5. The first control center login creates the encryption key pair (`config/center/pub`); the private key stays on that device.

## 5. Test the rules before trusting them (Rules Playground)

The rules were written but **not executed** by me (the Firebase emulator could not be downloaded in my environment). Console > Realtime Database > Rules > **Rules Playground**: use "Authenticated", set the provider and a custom token payload. If `email_verified` or `phone_number` is not accepted in the playground payload, tell me the exact error and I will adapt the rule expressions.

| # | Auth | Operation | Path | Expected |
|---|------|-----------|------|----------|
| 1 | none | read | `/users` | **denied** |
| 2 | none | read | `/facilities` | **denied** |
| 3 | uid A, email_verified=false, phone_number set | read | `/directory` | **denied** |
| 4 | uid A, both verified | read | `/directory` | allowed |
| 5 | uid A, both verified | write | `/users/A` with name, phone, email, setupDone | allowed (phone/email must equal the token's) |
| 6 | uid A, both verified | write | `/users/B` | **denied** |
| 7 | uid A, both verified | write | `/roles/A` = "CENTER" | **denied** |
| 8 | uid A, both verified, not center | read | `/incidents` | **denied** |
| 9 | uid A, both verified | write | `/incidents/A_123/report` | allowed |
| 10 | uid A, both verified | write | `/incidents/B_123/report` | **denied** |
| 11 | uid C with `/roles/C = "CENTER"` | read | `/incidents` | allowed |
| 12 | uid C (center) | write | `/community/<id>/hidden` = true | allowed |
| 13 | uid A | write | `/community/<id>/hidden` = true | **denied** |
| 14 | uid A | write | `/community/new` with text "hello hello" | **denied** (text < 15 chars) |
| 15 | uid A, listed in `/muted/A` | write | a valid post | **denied** |
| 16 | uid A | read | `/feedback` | **denied** |
| 17 | uid C (center) | read | `/feedback`, `/reports`, `/audit` | allowed |

Use realistic UIDs and set the simulated time to now when testing post creation (the rule accepts timestamps within 5 minutes).

## 6. Facility data for the offline directory

* Hotlines ship inside the app (`app/src/main/assets/hotlines.json`), each with a source link. Only numbers with a cited public source are included (10 hotlines, each with its source link). Add others after you verify them, and set `verified` only with a `sourceUrl`.
* Facilities: run `java tools/OsmImport.java app/src/main/assets/facilities_seed.json` on your laptop (needs internet), then rebuild. This copies OpenStreetMap data (c) OpenStreetMap contributors, ODbL, and marks every phone number **unverified**. Keep the attribution in your app/report. Google Maps data must not be bulk-copied for offline use; an official government list is a better source if you can get one.
* To push a corrected list to all users: write facility objects under `facilities/<id>` (control center only) with `kind` (`HOSPITAL`, `FIRE`, `POLICE`), `name`, `lat`, `lon`, optional `phone`, `phoneVerified`, `district`, `source`, `sourceUrl`. Signed-in users receive them with **Refresh**; the screen shows the last-updated date. The directory itself works without login using the bundled/cached data only.

## 7. Known gaps

* Rate limiting of posts is on the phone only; real server-side limits need Cloud Functions or App Check.
* A user who knows another person's phone hash could register `phoneIndex/<hash>` first (it only affects the center's "find user by phone" lookup). Fixing it needs a server (Cloud Functions).
* Disabling or revoking an account takes effect when its ID token next refreshes (up to about an hour) unless you also change `roles/` or add the user to `muted/`.


## Added later: new database nodes and rule test cases

New nodes in `database.rules.json`:
- `facilityReports`: a verified user can report a phone number for a hospital, fire station or police station. Only the control center deletes them (after approving into `facilities/{id}`).
- `sightings`: a verified user can send a private sighting of a missing person (type SIGHTING or FOUND). Only the control center reads them.
- `records`: control center writes only. A WANTED record must have a non-empty `authority`, `ref` and an https `sourceUrl`.

Try these in the Rules Playground before relying on them:
1. Verified user, write `facilityReports/x` with valid fields: allowed.
2. Same user reads `facilityReports`: denied.
3. Signed-in user without verified email/phone: denied.
4. Citizen writes `records/x`: denied.
5. Center writes a WANTED record without `sourceUrl`: denied. With authority, ref and https link: allowed.

## Public alert sources

The Alerts screen merges: USGS earthquakes (keyless public API, filtered to the Bangladesh region), GDACS global disaster RSS (filtered to Bangladesh by text), and the control center's own feed and broadcasts. Bangladesh Meteorological Department, FFWC and DDM publish no usable machine feed, so the app links to their sites. Google and Meta offer no public alert or missing-person API for Bangladesh.

## The single control center account

1. Firebase Console > Authentication > Users > Add user: one email and password that only you know. No phone number is needed.
2. Copy that user's UID. Realtime Database > add `roles/<that UID>` with the text value `CENTER`.
3. Publish the updated `database.rules.json` (the center role now passes without a phone number).
4. On any phone: open the login screen, tap the grey "Sign in to continue" line 5 times, choose **Control center**, sign in. Citizens never see that option, and the server still refuses anyone without the CENTER role.
5. The first phone that signs in as control center creates the key that decrypts Nearby-relay SOS packets and keeps it on that phone only. Sign in on that phone first. A second phone can use the map, chat and cases, but cannot decrypt relay packets.
6. In the center settings (Numbers) set the control center SMS number so the SMS fallback works.

## Other new screens

* **News**: public RSS feeds in `assets/feeds.json` (Google News searches). Emergencies, alerts and violence are sorted to the top. Edit the file to add or remove feeds. A feed that fails is skipped.
* **Alerts**: the same file lists alert searches and official source pages that open inside the app.
* **Talk to someone**: a scripted support companion for people with suicidal thoughts. It is not an AI and not a therapist. If words suggest danger, the user gets a 45 second countdown to cancel; if not cancelled, the emergency contacts get an SMS and the control center gets a "Mental health crisis" case with the location. The conversation is never saved or sent. Helpline numbers come from a public directory and should be checked.
* **Live map** (control center): active SOS cases and disaster check-ins only.

## BloodLink, regions and the ingest tool

* New nodes in the rules: `donors` (own profile only; phone NOT stored here) and `donorContacts/{uid}` (the donor's verified phone; readable by verified users only when the donor set `showPhone` to true, and it must equal the phone on their login token), `bloodRequests` (owner or control center edits) and
  `bloodResponses` (a donor's offer, readable only by the requester and the control center). Test in the Rules Playground:
  a verified user writes only their own `donors/{uid}`; a stranger cannot read `bloodResponses/{requestId}`; units of 11 are refused.
* BloodLink is voluntary and free. Group matching is a hint; the hospital screens and cross-matches. It does not send push
  notifications to donors yet: donors see open requests when they open the screen.
* Alerts have a Region filter (8 divisions). An alert that names other districts only is hidden; one that names no district stays (national).
* `backend/` is a separate pure-Java tool (Maven, PDFBox) that publishes crime statistics from a police PDF. See `backend/README.md`.

## Update 7: donors with consent, law-and-order alerts, assistant

* Publish the new `database.rules.json` again. New: `donorContacts/{uid}`, and `area` and `showPhone` on `donors`.
  Rules Playground checks: a verified user reads `donorContacts/X` only when `donors/X/showPhone` is true; writing a phone that is
  not your own login phone is refused; a user cannot write another user's `donorContacts`.
* Control-center broadcasts may carry `category` (`EMERGENCY`, `LAW_ORDER`, `SAFETY_NEWS`), `district` (one of the 64) and `level` (0-2).
  Those are the only items the app shows as OFFICIAL. News stays labelled as news.
* The AI assistant uses the Groq key typed into the phone (assistant > Key). Nothing is stored in Firebase or in git.
