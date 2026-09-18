# 📅 Simkl Calendar

Simkl Calendar is a modern Android application that integrates with your [Simkl](https://simkl.com/) account to provide a unified calendar for tracking upcoming TV shows, anime, and movies. Stay updated with personalized notifications and manage your watchlist with ease.

## ✨ Features

- **Unified Calendar**: View all your tracked TV shows, anime, and movies in a single, easy-to-read calendar.
- **Personalized Alerts**: Receive notifications when new episodes or movies from your watchlist are aired.
- **Quick Actions**: Mark episodes or entire seasons as watched directly from the notification.
- **Torrent Search**: Integrated torrent search functionality to find releases quickly.
- **Downloader Integration**: Seamlessly works with the [Torrent HTTP Downloader](https://github.com/felixbrucker/torrent-http-downloader-app) app.
- **Modern UI**: Built with Jetpack Compose and Material 3 for a clean and responsive experience.
- **Offline Support**: Uses Room database for local caching and offline access.

## 🛠️ Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Architecture**: MVVM (Model-View-ViewModel) with Single-Responsibility principles.
- **Networking**: [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Local Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Dependency Management**: Gradle Version Catalog
- **Code Coverage**: [Kover](https://github.com/Kotlin/kotlinx-kover)

## 📥 Installation & Updates

### 🚀 Using Obtainium (Recommended)

To stay updated with the latest versions, we recommend using **[Obtainium](https://github.com/ImranR98/Obtainium)**.

1. Install Obtainium on your Android device.
2. Open Obtainium and tap **"Add App"**.
3. Paste this repository's URL: `https://github.com/felixbrucker/simkl-calendar`
4. Obtainium will notify you and help you install updates automatically whenever a new build is available on GitHub.

### 📦 Manual Download

You can find the latest APKs in the [Releases](https://github.com/felixbrucker/simkl-calendar/releases) section.

## 🏗 Development

### Prerequisites
- Android Studio Ladybug (or newer)
- Java 25
- Android SDK 37

### Build Commands
```bash
# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# Generate coverage report
./gradlew koverHtmlReport
```

## 📄 License
This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
