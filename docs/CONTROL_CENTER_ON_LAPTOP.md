# Running the Control Center on your laptop (no separate desktop app)

The Control Center is the same Android app, signed in with a control-center account (see FIREBASE_SETUP.md step 4).
Run it in the Android Studio emulator on your laptop. Citizens use real phones, a second emulator, or the simulator below.

## Emulator
1. Android Studio > Device Manager > Create device > pick an image **with Google Play** (needed for Firebase Phone Auth / Play Integrity), API 34.
2. Run the app on it, tap **Log in**, choose **Control center**, sign in with the control-center email and password.
3. Phone verification on an emulator: use the **test phone numbers** from FIREBASE_SETUP.md (no SMS arrives on an emulator).
4. Keep the emulator open: the center polls Firebase every 20 seconds and shows incidents, the map, disaster check-ins and the community moderation tools.

## Testing without many phones
Create one normal citizen account and verify it fully (phone, email, password). Then, on your laptop:

    java tools/SimulateIncidents.java <WEB_API_KEY> <email> <password> 15

The Web API Key is in Console > Project settings > General. It writes 15 incidents labelled `[SIMULATION]` around Dhaka (type, location, battery vary). They appear in the center's list and on its map. Use the center to acknowledge, dispatch and close them. Nothing is sent to real emergency services.

## What an emulator cannot do
* Receive SMS or Nearby/Bluetooth relay packets from real phones, so only the **internet** channel reaches an emulated center. For the SMS and relay demos use one real phone as the center.
* Press a physical power button (the 5-press trigger) or hold hardware volume keys for the citizen role; test those on a real phone.
