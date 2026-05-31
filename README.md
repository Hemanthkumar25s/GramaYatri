# 🚌 GramaYatri — Real-Time Rural Bus Tracking

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Firebase-Realtime%20DB-FFCA28.svg?logo=firebase&logoColor=black)](https://firebase.google.com)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?logo=android&logoColor=white)](https://developer.android.com/studio/releases/platforms)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**GramaYatri** is an Android application that brings real-time bus tracking to rural commuters. It combines live GPS data from on-bus ticketing machines, driver phones, and crowd-sourced passenger pings to provide accurate ETAs and route information — even in areas with limited infrastructure.

> **Part of the GramaYatri ecosystem:** [Passenger App](https://github.com/Hemanthkumar25s/GramaYatri) · [Driver App](https://github.com/Hemanthkumar25s/GramaYatri-Driver) · [Ticket Machine](https://github.com/Hemanthkumar25s/GramaYatri-TicketMachine)

---

## ✨ Features

- **📍 Live Bus Tracking** — Real-time bus locations on an interactive map
- **🎯 Accurate ETAs** — Combines ticketing machine GPS, driver GPS, and passenger pings for the best estimate
- **🗺️ Route Discovery** — Browse and search nearby bus routes and stops
- **🔔 Smart Notifications** — Alerts for delays, cancellations, and approaching buses
- **📴 Offline Support** — Access saved routes and schedules with local Room caching
- **👥 Crowd-Sourced Pings** — Passengers can share their bus location to help fellow commuters

## 🏗️ System Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Ticket Machine  │     │  Driver Phone    │     │  Passenger App  │
│  (Primary GPS)   │     │  (Fallback GPS)  │     │  (This Project) │
│                  │     │                  │     │                  │
│  GPS → Firebase  │     │  QR-verified     │     │  Reads from     │
│  Shows QR for    │     │  GPS broadcast   │     │  Firebase,      │
│  driver verify   │     │                  │     │  shows map      │
└────────┬─────────┘     └────────┬─────────┘     └────────┬─────────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  ▼
                        ┌──────────────────┐
                        │    Firebase      │
                        │  Realtime DB     │
                        │                  │
                        │ live_locations/  │
                        │   {routeId}/     │
                        │   ├─ TICKET_...  │
                        │   ├─ DRIVER/     │
                        │   └─ PASSENGER/  │
                        └──────────────────┘
```

**Source priority:** `TICKET_MACHINE` → `DRIVER` → `PASSENGER`

## 🛠 Tech Stack

| Category | Technology |
|----------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **Language** | Kotlin 2.0+ |
| **Architecture** | MVVM + Clean Architecture (use-cases, repositories, UI layers) |
| **DI** | Hilt (Dagger) |
| **Database** | Firebase Realtime Database (live) + Room (offline cache) |
| **Maps** | Google Maps Compose |
| **Background** | WorkManager + Foreground Services |
| **Navigation** | Compose Navigation |
| **Networking** | Ktor Client |
| **Serialization** | Kotlinx Serialization |

## 📱 Screenshots

*(Coming soon — add your app screenshots here)*

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1+) or newer
- JDK 17+
- A Firebase project with Realtime Database enabled
- A Google Maps API key

### Setup

```bash
# Clone the repository
git clone https://github.com/Hemanthkumar25s/GramaYatri.git
cd GramaYatri
```

1. **Firebase Setup**
   - Create a project in [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app with package name `com.gramayatri`
   - Download `google-services.json` and place it in `app/`
   - Enable: Realtime Database, Authentication (Anonymous), Cloud Messaging

2. **Google Maps**
   - Get an API key from [Google Cloud Console](https://console.cloud.google.com/)
   - Add to `local.properties`: `MAPS_API_KEY=YOUR_KEY_HERE`

3. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or open in Android Studio and click **Run**.

## 📁 Project Structure

```
GramaYatri/
├── app/
│   ├── src/main/java/com/gramayatri/
│   │   ├── data/           # Models, repositories, workers
│   │   ├── di/             # Hilt dependency injection modules
│   │   ├── domain/         # Use cases and business logic
│   │   └── ui/             # Compose screens (auth, home, search, etc.)
│   └── build.gradle.kts
├── scripts/                # Demo and benchmark scripts
├── loadtesting/            # JMeter & Locust load test files
├── firebase_rules.json     # Firebase Realtime DB security rules
└── karnataka_routes_with_coords.json  # Route data
```

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedCheck
```

## 🤝 Contributing

Contributions are welcome! Please open an issue or submit a pull request.

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file.

---

<p align="center">Developed with ❤️ by <a href="https://github.com/Hemanthkumar25s">Hemanth Kumar S</a></p>
