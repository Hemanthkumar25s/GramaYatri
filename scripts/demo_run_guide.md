# GramaYatri — Demo Run Guide

This guide describes how to run all three GramaYatri apps on physical devices
and/or Android emulators to demonstrate the full trip flow.

## Prerequisites

- Android Studio (latest stable)
- Physical device(s) with USB debugging enabled, **OR** AVD(s) created via AVD Manager
- Firebase project configured (google-services.json must exist in each app directory)
- Minimum API level: **24** (Android 7.0)
- Target API level: **35** (Android 15)

---

## 1. Setup & Build

### Clone and prepare

```bash
# Build all three apps
cd GramaYatri
./gradlew assembleDebug

cd ../GramaYatri-Driver
./gradlew assembleDebug

cd ../GramaYatri-TicketMachine
./gradlew assembleDebug

cd ..
```

### Install on devices / emulators

```bash
# List connected devices
adb devices -l

# Install each APK
adb install -r GramaYatri/app/build/outputs/apk/debug/app-debug.apk
adb install -r GramaYatri-Driver/app/build/outputs/apk/debug/app-debug.apk
adb install -r GramaYatri-TicketMachine/app/build/outputs/apk/debug/app-debug.apk
```

> **Tip:** Use `adb -s <serial> install ...` to target a specific device when
> multiple devices are connected.

---

## 2. Launch All Three Apps

Open three terminal windows (one per app) and run the launch commands below.

> **Note:** Replace `<package>/<activity>` with the actual launchable activity
> from the AndroidManifest.xml of each app.

### GramaYatri (User App)

```bash
adb shell am start -n "com.gramayatri/.GramaYatriApp"
```

### GramaYatri-Driver (Driver App)

```bash
adb shell am start -n "com.gramayatri.driverapp/.DriverActivity"
```

### GramaYatri-TicketMachine (Ticket Machine App)

```bash
adb shell am start -n "com.gramayatri.ticketmachine/.TicketMachineActivity"
```

> To verify exact activity names:
> ```bash
> adb shell dumpsys package <package-name> | grep -A 2 "Main Activity"
> ```

---

## 3. Demo Flow — Step by Step

### 3.1 TicketMachine (Start the source of truth)

1. Open **TicketMachine** app
2. Enter a **Route ID** (e.g. `KSRTC-DEMO-101`)
3. Toggle **Demo Mode** ON (simulates GPS without real location hardware)
4. Tap **START GPS**
   - ✅ Status changes to *"✓ Verified GPS is LIVE [DEMO]"*
   - ✅ Trip ID and QR payload are displayed
5. Tap **SHOW DRIVER QR** to display a QR code
   - This QR encodes: `gramayatri://driver-verify?routeId=...&tripId=...&machineId=...&token=...`

### 3.2 Driver App (Verify against TicketMachine)

1. Open **Driver** app
2. Configure the app with the same **Route ID** as the TicketMachine
3. The Driver app should show the trip dashboard
4. Use the **Scan QR** feature to scan the QR code from the TicketMachine
   - ✅ Driver gets verified against the ticket machine's session
5. The driver's GPS data now appears on the route with VERIFIED status

### 3.3 GramaYatri User App (See the bus live)

1. Open **GramaYatri** (User) app
2. Browse to the Route ID used above (e.g. `KSRTC-DEMO-101`)
3. The live bus location from the TicketMachine should appear on the map
   - ✅ Bus icon visible with live GPS data
   - ✅ Trip dashboard shows routes, stops, and ETA
4. Tap on a **Stop** to see estimated arrival time

---

## 4. Automated Demo Script

Run this script to launch all three apps simultaneously with a single command:

```bash
#!/bin/bash
# demo_run.sh — Launch all 3 apps in parallel

echo "=== GramaYatri Demo Run ==="

echo "Launching TicketMachine..."
adb shell am start -n "com.gramayatri.ticketmachine/.TicketMachineActivity"

sleep 1

echo "Launching Driver App..."
adb shell am start -n "com.gramayatri.driverapp/.DriverActivity"

sleep 1

echo "Launching User App..."
adb shell am start -n "com.gramayatri/.GramaYatriApp"

echo "=== All apps launched! ==="
echo "Configure TicketMachine first (Route ID + Start GPS)"
echo "Then configure Driver to match"
echo "Then use User app to see live tracking"
```

---

## 5. Cold-Start Benchmark

To measure cold-start time (< 2s target):

```bash
# Cold-start benchmark for each app
./GramaYatri/scripts/cold_start_benchmark.sh
```

> See `GramaYatri/scripts/cold_start_benchmark.sh` for detailed timing output
> with p50/p95/p99 latency percentiles.

---

## 6. Running Instrumentation Tests

```bash
# For each app
cd GramaYatri
./gradlew connectedCheck

cd ../GramaYatri-Driver
./gradlew connectedCheck

cd ../GramaYatri-TicketMachine
./gradlew connectedCheck
```

---

## 7. Running on Multiple Devices in Parallel

If you have multiple physical devices or emulators, you can target specific ones:

```bash
# Get device serial numbers
adb devices

# Install on specific devices
adb -s emulator-5554 install -r GramaYatri/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install -r GramaYatri-Driver/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5558 install -r GramaYatri-TicketMachine/app/build/outputs/apk/debug/app-debug.apk

# Launch on specific devices
adb -s emulator-5554 shell am start -n "com.gramayatri/.GramaYatriApp"
adb -s emulator-5556 shell am start -n "com.gramayatri.driverapp/.DriverActivity"
adb -s emulator-5558 shell am start -n "com.gramayatri.ticketmachine/.TicketMachineActivity"
```

---

## 8. Troubleshooting

| Problem | Solution |
|---------|----------|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Uninstall existing version: `adb uninstall <package>` |
| Location not working | Grant location permission in Settings > Apps |
| Firebase not connecting | Verify `google-services.json` exists for each app |
| `FAILED_BINDER_TRANSACTION` | Reduce intent extra size or restart device |
| QR code not scanning | Ensure 512×512 display density on device |
