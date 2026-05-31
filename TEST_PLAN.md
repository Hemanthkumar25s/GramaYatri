# GramaYatri — Integration & Load Testing Plan

## 1. Overview

This document defines the strategy for integration testing all three GramaYatri Android applications and load testing them to ensure they meet performance targets.

### Apps Under Test

| App | Package | Target Users | Load Target |
|-----|---------|-------------|-------------|
| **GramaYatri** (User) | `com.gramayatri` | 10,000 concurrent | App launch < 2s |
| **GramaYatri-Driver** | `com.gramayatri.driver` | 5,000 concurrent | App launch < 2s |
| **GramaYatri-TicketMachine** | `com.gramayatri.ticketmachine` | 5,000 concurrent | App launch < 2s |

---

## 2. Existing Unit Tests

### JVM Unit Tests (run on CI / local JVM)

| App | Test File | What It Covers |
|-----|-----------|----------------|
| GramaYatri-Driver | `DriverConfigTest.kt` | QR payload parsing, config validation, URL encoding |
| GramaYatri-TicketMachine | `TicketMachineConfigTest.kt` | Trip ID generation, QR payload format, verification token |
| GramaYatri (User) | *(none yet)* | — |

**Run command:** `./gradlew testDebugUnitTest`

### Android Instrumentation Tests (run on emulator/device)

| App | Test File | What It Covers |
|-----|-----------|----------------|
| GramaYatri (User) | `MainActivityTest.kt` | App launch verification, splash screen |
| GramaYatri (User) | `GramaYatriAppComposeTest.kt` | Compose UI: splash screen rendering |
| GramaYatri-TicketMachine | `TicketMachineActivityTest.kt` | Setup form, route entry, demo mode, stop/start, settings |

**Run command:** `./gradlew connectedAndroidTest`

---

## 3. Integration Test Strategy

### 3.1 Firebase Integration Tests

Since all three apps share Firebase Realtime Database, run these integration tests:

```bash
# Placeholder: Execute these via Firebase Emulator Suite or a test Firebase project
# 1. Start Firebase Emulators
firebase emulators:start --only database

# 2. Run instrumented tests against emulator
./gradlew connectedAndroidTest
```

**Test Scenarios:**

| Scenario | Steps | Expected Outcome |
|----------|-------|------------------|
| **Complete Trip Flow** | 1. TicketMachine starts GPS broadcast → 2. Driver scans QR from TicketMachine → 3. User app shows live bus data | Live location visible in user app within 5s |
| **Driver Verification** | 1. TicketMachine publishes session → 2. Driver scans and verifies → 3. Firebase verification record created | Session claimed successfully |
| **Crowd-sourced Ping** | 1. User sends a ping at a stop → 2. Another user confirms/denies | Ping visible within 3s |
| **Alert Propagation** | 1. Admin creates alert in Firebase → 2. User app receives FCM notification | Alert appears on home screen |

### 3.2 End-to-End Flow Tests (Manual / Scripted)

Use **Android Debug Bridge (ADB)** for scripted integration tests:

```bash
# Launch TicketMachine with specific route
adb shell am start -n com.gramayatri.ticketmachine/.TicketMachineActivity

# Launch Driver app
adb shell am start -n com.gramayatri.driver/.DriverActivity

# Launch User app
adb shell am start -n com.gramayatri/.MainActivity
```

### 3.3 Firebase Test Lab (Recommended)

Submit all three APKs to Firebase Test Lab for automated device-farm testing:

```bash
# Build APKs
./gradlew assembleDebug

# Submit to Firebase Test Lab
gcloud firebase test android run \
  --app GramaYatri/app/build/outputs/apk/debug/app-debug.apk \
  --test GramaYatri/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Nexus6P,version=27,locale=en,orientation=portrait \
  --device model=Nexus6P,version=28,locale=en,orientation=portrait \
  --device model=Pixel2,version=29,locale=en,orientation=portrait \
  --timeout 30m
```

---

## 4. Load & Performance Testing

### 4.1 App Cold Start Benchmark

Measure cold-start time using ADB:

```bash
# For User app (target: < 2000ms)
adb shell am start -W -n com.gramayatri/.LaunchActivity | grep "TotalTime"

# For Driver app (target: < 2000ms)
adb shell am start -W -n com.gramayatri.driver/.DriverActivity | grep "TotalTime"

# For TicketMachine app (target: < 2000ms)
adb shell am start -W -n com.gramayatri.ticketmachine/.TicketMachineActivity | grep "TotalTime"
```

**Procedure:**
1. Force-stop the app: `adb shell am force-stop <package>`
2. Wait 2 seconds
3. Launch and capture `TotalTime`
4. Repeat 10 times, calculate average
5. Compare against target: **cold start < 2000ms**

### 4.2 Firebase Concurrent Access Test (JMeter)

Use Apache JMeter with the Firebase REST API to simulate concurrent database access:

```
Thread Group:
  - Number of Threads (users): 10,000 (User app), 5,000 (Driver), 5,000 (TicketMachine)
  - Ramp-up period: 60 seconds
  - Loop Count: 10

HTTP Request Defaults:
  - Protocol: https
  - Server: <project>.firebaseio.com

Endpoints to test:
  - GET /live_locations.json
  - GET /routes.json
  - PUT /live_locations/{routeId}/DRIVER.json
  - PUT /ticket_machine_sessions/{tripId}.json
```

### 4.3 Locust Load Test (Python)

Create a `locustfile.py` for simulating real user behavior:

```python
from locust import HttpUser, task, between
import random

class GramaYatriUser(HttpUser):
    wait_time = between(1, 5)

    @task(3)
    def view_routes(self):
        self.client.get("/routes.json")

    @task(2)
    def view_live_locations(self):
        route_id = f"KSRTC-{random.randint(1, 200)}"
        self.client.get(f"/live_locations/{route_id}.json")

    @task(1)
    def submit_ping(self):
        route_id = f"KSRTC-{random.randint(1, 200)}"
        self.client.put(f"/pings/{route_id}/ping.json", json={...})

class DriverUser(HttpUser):
    wait_time = between(2, 8)

    @task(2)
    def update_location(self):
        route_id = f"KSRTC-{random.randint(1, 200)}"
        self.client.put(f"/live_locations/{route_id}/DRIVER.json", json={...})

    @task(1)
    def verify_session(self):
        trip_id = f"trip-{random.randint(1, 1000)}"
        self.client.get(f"/ticket_machine_sessions/{trip_id}.json")
```

**Run:** `locust -f locustfile.py --headless -u 20000 -r 200 -H https://<project>.firebaseio.com`

### 4.4 Performance Targets

| Metric | User App | Driver App | TicketMachine |
|--------|----------|------------|---------------|
| Cold start time | < 2,000 ms | < 2,000 ms | < 2,000 ms |
| Warm start time | < 500 ms | < 500 ms | < 500 ms |
| Firebase read (p95) | < 500 ms | < 500 ms | < 500 ms |
| Firebase write (p95) | < 800 ms | < 800 ms | < 800 ms |
| APK size | < 15 MB | < 10 MB | < 10 MB |
| Memory usage | < 128 MB | < 96 MB | < 96 MB |

---

## 5. CI/CD Integration (GitHub Actions)

Create `.github/workflows/test.yml`:

```yaml
name: Android Tests

on: [push, pull_request]

jobs:
  unit-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: User App Unit Tests
        run: cd GramaYatri && ./gradlew testDebugUnitTest

      - name: Driver App Unit Tests
        run: cd GramaYatri-Driver && ./gradlew testDebugUnitTest

      - name: TicketMachine Unit Tests
        run: cd GramaYatri-TicketMachine && ./gradlew testDebugUnitTest

  instrumentation-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build all APKs
        run: |
          cd GramaYatri && ./gradlew assembleDebug assembleDebugAndroidTest
          cd GramaYatri-Driver && ./gradlew assembleDebug assembleDebugAndroidTest
          cd GramaYatri-TicketMachine && ./gradlew assembleDebug assembleDebugAndroidTest

      - name: Firebase Test Lab
        uses: google-github-actions/firebase-test-lab@v2
        with:
          args: >
            run --type instrumentation
            --app GramaYatri/app/build/outputs/apk/debug/app-debug.apk
            --test GramaYatri/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

---

## 6. Monitoring

Use **Firebase Performance Monitoring** and **Crashlytics** in all three apps:

1. Add `com.google.firebase:firebase-perf` dependency
2. Enable automatic screen rendering tracing
3. Set custom trace for cold start:

```kotlin
val trace = FirebasePerformance.getInstance().newTrace("cold_start")
trace.start()
// ... app initialization ...
trace.stop()
```

Monitor in Firebase Console:
- **App Start Time** → track against < 2s target
- **Network Requests** → track Firebase read/write latency
- **ANR Rate** → should be < 0.1%
- **Crash-free Users** → target > 99.5%

---

## 7. Tools Required

| Tool | Purpose | Link |
|------|---------|------|
| Android Studio | Running instrumentation tests | developer.android.com/studio |
| Firebase Test Lab | Automated device-farm testing | firebase.google.com/docs/test-lab |
| Apache JMeter | HTTP load testing | jmeter.apache.org |
| Locust | Python-based load testing | locust.io |
| ADB | Cold-start benchmarking | developer.android.com/studio/command-line/adb |
| Gradle Profiler | Build performance analysis | github.com/gradle/gradle-profiler |
| Firebase Console | Performance & crash monitoring | console.firebase.google.com |

---

## 8. Quick-Start Commands

```bash
# Step 1: Run unit tests
cd GramaYatri && ./gradlew testDebugUnitTest
cd GramaYatri-Driver && ./gradlew testDebugUnitTest
cd GramaYatri-TicketMachine && ./gradlew testDebugUnitTest

# Step 2: Run instrumentation tests on connected device/emulator
cd GramaYatri && ./gradlew connectedAndroidTest
cd GramaYatri-Driver && ./gradlew connectedAndroidTest
cd GramaYatri-TicketMachine && ./gradlew connectedAndroidTest

# Step 3: Measure cold start
adb shell am force-stop com.gramayatri
adb shell am start-W -n com.gramayatri/.LaunchActivity | grep TotalTime

adb shell am force-stop com.gramayatri.driver
adb shell am start-W -n com.gramayatri.driver/.DriverActivity | grep TotalTime

adb shell am force-stop com.gramayatri.ticketmachine
adb shell am start-W -n com.gramayatri.ticketmachine/.TicketMachineActivity | grep TotalTime
```
