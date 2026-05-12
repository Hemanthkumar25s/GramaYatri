# GramaYatri

**GramaYatri** is a real-time transit Android application designed to empower rural commuters with live bus tracking and community-powered arrival estimates. Built using modern Android development practices, it aims to reduce waiting times and improve the reliability of public transport in areas with limited infrastructure.

## 🚀 Features

- **Live Bus Tracking:** Real-time location updates for buses on supported routes.
- **Crowd-sourced ETAs:** Accurate arrival estimates based on pings from other passengers.
- **Route Discovery:** Browse and search for local bus routes and stops.
- **Smart Notifications:** Get alerts for delays, cancellations, or when a bus is approaching your stop.
- **Offline Support:** Access saved routes and schedules even without an active internet connection using local caching.
- **Interactive Maps:** Visual representation of routes, stops, and current bus locations.

## 🛠 Tech Stack

- **UI:** Jetpack Compose (Modern declarative UI)
- **Language:** Kotlin
- **Database:** 
  - **Firebase Realtime Database:** For live location syncing and pings.
  - **Room:** For local persistence and offline capabilities.
- **Dependency Injection:** Hilt (Dagger)
- **Networking:** Ktor Client
- **Background Tasks:** WorkManager & Foreground Services
- **Navigation:** Compose Navigation
- **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **Serialization:** Kotlinx Serialization

## 📱 Screenshots

*(Coming Soon)*

## 🛠 Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Hemanthkumar25s/GramaYatri.git
   ```
2. **Open in Android Studio:**
   Import the project into Android Studio (Hedgehog or newer recommended).
3. **Firebase Setup:**
   - Create a project in the [Firebase Console](https://console.firebase.google.com/).
   - Add an Android app with the package name `com.gramayatri`.
   - Download the `google-services.json` file and place it in the `app/` directory.
   - Enable Realtime Database, Authentication (Anonymous or Email), and Cloud Messaging.
4. **Google Maps API:**
   - Obtain a Google Maps API key from the [Google Cloud Console](https://console.cloud.google.com/).
   - Add your API key to `local.properties`:
     ```properties
     MAPS_API_KEY=YOUR_API_KEY_HERE
     ```
5. **Build & Run:**
   Sync Gradle and run the app on an emulator or physical device.

## 🤝 Contributing

Contributions are welcome! If you have suggestions for new features or improvements, please feel free to open an issue or submit a pull request.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
Developed with ❤️ by [Hemanth Kumar S](https://github.com/Hemanthkumar25s)
