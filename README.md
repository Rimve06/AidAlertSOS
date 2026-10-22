# AidAlert SOS - Emergency Aid Alert System (Android, Java + XML)

One Android Studio app with two roles chosen at registration:

| Role | What it does |
|------|--------------|
| **Citizen** | Triggers SOS with hardware buttons, chooses the emergency type, is tracked, chats, answers disaster check-ins, can volunteer as a nearby helper |
| **Control center** | Receives SOS (internet, SMS or relay), sees live map + trail, confirms with the user, dispatches ambulance / fire / police / rescue, asks nearby helpers, runs disaster check-ins, keeps incident records, publishes missing/wanted lists, talks to one / all users, monitors community chats |

Only course content is used: Java, XML layouts, view binding, RecyclerView, custom views, Services, BroadcastReceivers, ContentProvider, SQLite, `HttpURLConnection` + `org.json`, executors / threads, Git. No Kotlin, no WebView/HTML/JS.

## 1. Open and run

1. Android Studio (Koala or newer), JDK 17. `File > Open` the `AidAlertSOS` folder and let Gradle sync (AGP 8.5.2, Gradle 8.7 from `gradle/wrapper/gradle-wrapper.properties`).
2. Create a free Firebase project > **Realtime Database** > create database. Copy its URL, for example `https://myproject-default-rtdb.firebaseio.com`.
3. For development set the rules to (this is open - use only for testing, see section 7):
   ```json
   { "rules": { ".read": true, ".write": true } }
   ```
4. Install the app on two real phones (an emulator cannot press the power button or send SMS).
   - Phone A: *Control center* > register with access code `AIDCENTER-2026` (change it in `Config.java`). The first center registration creates the encryption key pair; the private key stays on that phone.
   - Phone B: *Citizen* > register, then follow the permissions checklist.
5. Enter the Firebase URL on the first screen (also editable in Settings).

## 2. Triggers (citizen phone)

| Trigger | Result |
|---------|--------|
| Power button **5 times** in a row | SOS, screen turns on, emergency-type page opens directly |
| Any touch, key or unlock between presses | Count resets to 0 (the "sequential" rule). More than 1.5 s between two presses also resets it |
| Hold **Volume Up** 2 s | Same as above (medical / accident / fire / violence / other) |
| Hold **Volume Down** 2 s | **Silent violence mode**: screen stays off, no sound/vibration, contacts are texted, the phone calls the preselected number (default: control center) so the operator can hear the surroundings. Ending it needs the account password |
| On-screen SOS button (hold 1.5 s) | Same as volume up |

Android never delivers the power key to apps. The guard service counts the screen ON/OFF broadcasts that each press causes. Volume keys and touches are read by a small accessibility service that reads no screen content. Both must be enabled in the permissions checklist.

## 3. What happens after a trigger

1. Incident is saved in SQLite and GPS tracking starts (position every ~8 s, kept as a trail).
2. Delivery, best channel first: **internet** (Firebase) -> **SMS** to the control center phone (parsed automatically there) -> **encrypted relay packet** for nearby phones.
3. The alert is sent *before* the user picks a type (type = unspecified), so no seconds are lost. The chosen type is sent as an update.
4. Contacts get an SMS with type + map link, and the phone auto-calls the preselected number once the type is chosen (or after 15 s).
5. The operator acknowledges, confirms with the user (call / chat), dispatches services (the dialer opens with the configured number; a human presses call), optionally asks volunteers within 3 km, then resolves. Status and "ambulance dispatched" are shown on the citizen's screen.

## 4. Offline chain

SIM SMS -> Nearby Connections relay (Bluetooth / Wi-Fi direct, `P2P_CLUSTER`). Every phone with the relay switch on swaps packets with phones it meets. Packets are **AES-GCM encrypted with a key wrapped by the control center's RSA public key**, so relaying phones and their owners cannot read them. The first phone that gets internet uploads them to `relay/`; the control center decrypts them and creates the incident. Duplicate packets are ignored and hops are capped at 8.

## 5. Other features

* **Disaster mode**: operator sends a check-in query; every app gets a notification. Users choose *I am safe* (one position) or *I need rescue* (distress mode, position every minute, switch off anytime which deletes the record). Dots appear on the map; the rescue list can be shared with the national rescue system.
* **Nearby helpers**: opt-in. The distance check runs on the helper's phone; nothing is uploaded until they tap *I can help*.
* **Records**: missing persons / wanted criminals published by the center are copied to every phone's SQLite database (readable offline) with a notification.
* **Two-way chat**: center <-> one user, center -> all users (announcement), user -> center. Community forum and user-to-user chat unlock **only while the user's own SOS is active**, and the center can read them.
* **Privacy**: location only after SOS / distress toggle / check-in / helper accept; user ids are hashes of the phone number; passwords are salted PBKDF2 hashes; public profile fields and phone visibility are opt-in; medical data attaches to an SOS only if enabled.

## 6. Code map

```
engine/   PowerTapDetector, SosEngine, SosGuardService (foreground service), KeyAccessibilityService,
          SmsReceiver / BootReceiver (BroadcastReceivers), Dispatcher, RelayManager, SyncManager, LocationHelper
data/     Db (SQLite), IncidentProvider (ContentProvider), Incident, Prefs, Msg
net/      FirebaseRest (HttpURLConnection + JSON), Crypto, SmsProtocol
view/     MapView, HoldButtonView  (custom views)
ui/       citizen screens;  ui/center/  control-center screens;  RowAdapter (RecyclerView)
```

Firebase layout: `users, directory, config/center, incidents/{id}/report|ops, tracks, relay, records, disasters, checkins, helpRequests, helpResponses, msgs/{uid}, broadcasts, forum, dm, dmIndex`.

Unit tests (`app/src/test`) cover the power-sequence rule, SMS format, encryption, password hashing and distance.

## 7. Known limits (be honest in your demo / report)

* **Not run on a device by the author of this code.** It was written without Android Studio or an emulator available: layouts/IDs, XML, manifest wiring and the pure-Java logic (10 unit tests) were checked, but a first Gradle sync may show small compile fixes. Test on real phones.
* Phone vendors (Xiaomi, Oppo, Samsung ...) kill background services: set battery to *Unrestricted* and allow auto-start.
* Android may block starting an activity from the background; the app then falls back to a full-screen notification. Keep "Show over other apps" enabled.
* Silent calling needs `CALL_PHONE`; Android does not let apps hide the call screen or force speaker/mic on the callee side. The operator hears the phone's surroundings through the normal call.
* A real emergency service cannot be "called" by the app: the control center is your receiver and dials the real services.
* **Security**: the test-mode Firebase rules above are open to everyone, including password hashes. For a real deployment use Firebase Authentication + per-path rules. The center access code and the single center key pair are student-project simplifications; only the phone that created the key can decrypt relay packets.
* Polling every 20 s replaces push notifications (no Firebase Cloud Messaging) to stay inside the course scope.
* Power-button counting uses screen on/off events, so the screen turning off by timeout can count as a press; the 1.5 s gap rule makes accidental triggers unlikely.
