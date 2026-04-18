# Smart Hydration Tracker (Android)

A Kotlin + Jetpack Compose Android app for personalized hydration tracking, smart reminders, BLE bottle integration (simulation-first), and historical insights.

This project was implemented from `hydration_app_plan.html` and is actively refined for production-style UX.

## Core capabilities

### Personalization & onboarding

- Bio onboarding with validation: name, age, weight, height, sex, activity, climate, health flag, wake/sleep time, reminder style.
- Personalized goal engine (weight/activity/climate/age-aware) with explanation text.
- Profile can be reviewed and edited in Settings.
- Profile edit **draft autosave** is persisted and restored if dialog is closed/reopened (including app restart).

### Tracking & dashboard

- Daily hydration progress ring.
- Last sip indicator + streak calculation.
- Manual quick-add buttons.
- BLE-triggered sip ingestion (simulation mode available now).

### History & insights

- 7-day history bars with goal-hit highlighting.
- Insight cards (best day, lowest day, average, pattern prompt).

### BLE & device flow

- Pairing screen with scan/connect/disconnect UX.
- Sensor mode architecture supports:
  - simulation mode now
  - real hardware implementation later

### Settings, exports, and UX polish

- Modern card-based settings sections with animated expand/collapse.
- Expand/collapse state persisted across app restarts.
- Unit toggle (`ml`/`fl oz`).
- Android 13+ notification permission request.
- CSV export workflow:
  - create export file
  - share latest export
  - open exports folder (with fallback)
  - recent exports list with per-file delete/share

## Screens

- Onboarding
- Bottle Pairing
- Dashboard
- History
- Insights
- Settings

## Tech stack

- Kotlin (JVM 17)
- Jetpack Compose + Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room (local hydration entries)
- DataStore Preferences (profile/settings/UI state)
- WorkManager (reminders)
- BLE gateway abstraction (simulation + real placeholder)

## Permissions used

- Bluetooth: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (+ legacy Bluetooth permissions for older Android)
- Location (BLE scan support on older versions): `ACCESS_FINE_LOCATION`
- Notifications: `POST_NOTIFICATIONS` (Android 13+)

## Project layout (important files)

- `app/src/main/java/com/tamim/hydrationtracker/MainActivity.kt` — app bootstrap + mode wiring
- `app/src/main/java/com/tamim/hydrationtracker/ui/HydrationApp.kt` — navigation + all Compose screens
- `app/src/main/java/com/tamim/hydrationtracker/ui/HydrationViewModel.kt` — screen state + actions
- `app/src/main/java/com/tamim/hydrationtracker/data/repo/HydrationRepository.kt` — DataStore + Room repository
- `app/src/main/java/com/tamim/hydrationtracker/domain/GoalCalculator.kt` — goal formula and explanation
- `app/src/main/java/com/tamim/hydrationtracker/ble/BottleSensorGateway.kt` — BLE interface
- `app/src/main/java/com/tamim/hydrationtracker/ble/FakeBleManager.kt` — simulation BLE manager
- `app/src/main/java/com/tamim/hydrationtracker/ble/RealBottleSensorGateway.kt` — real BLE placeholder
- `app/src/main/java/com/tamim/hydrationtracker/work/ReminderWorker.kt` — reminder worker
- `app/src/main/AndroidManifest.xml` — app permissions + `FileProvider`

## Build/config snapshot

- Android Gradle Plugin: `8.7.2` (root `build.gradle.kts`)
- Kotlin: `1.9.24`
- Compile/target SDK: `35`
- Min SDK: `26`

## Run locally

### Android Studio (recommended)

1. Open `/Users/tamimchowdhury/Muc project` in Android Studio.
2. Let Gradle sync complete.
3. Run the `app` module on emulator/device.

### Optional CLI (if wrapper is present)

```bash
cd "/Users/tamimchowdhury/Muc project"
./gradlew assembleDebug
./gradlew test
```

> Note: In this environment, Gradle CLI execution may fail if `gradlew` or system `gradle` is missing.

## Current hardware integration status

- The app is usable now without physical bottle hardware.
- `SensorMode.SIMULATION` supports development/testing flow.
- When bottle firmware is ready, implement scanner/GATT behavior in `RealBottleSensorGateway` and switch mode in `MainActivity`.

## Roadmap ideas

- Replace BLE placeholder with production `BluetoothLeScanner` + GATT characteristic parsing.
- Add richer charts (e.g., Vico) and calendar heatmap.
- Add Hilt dependency injection.
- Add rename support for exported CSV files.
