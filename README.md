# My Application - Android Music Player

An Android music player built with Kotlin and Jetpack Compose.  
The app integrates multiple online music platforms (NetEase Cloud Music, QQ Music, and KuWo), and provides a complete listening workflow including search, toplists, playlists, favorites, recent plays, daily recommendations, lyrics, and full-screen playback controls.

## Features

- Multi-platform music search with pagination and platform switching
- Toplists and playlist browsing (including cached home content)
- Full player experience: mini player, full-screen player, lyrics, queue control, playback mode switching
- Local library management: favorites, recent plays, custom playlists
- Smart recommendation flows: daily recommendations, FM-style random picks, similar tracks
- URL/playback resolving with platform-specific fallback strategies
- Glassmorphism-inspired Compose UI with light/dark theme support

## Tech Stack

- Language: Kotlin (100%)
- UI: Jetpack Compose + Material 3
- Architecture: Single-Activity, MVVM + Clean Architecture
- DI: Hilt (Dagger)
- Networking: Retrofit + OkHttp + Kotlinx Serialization
- Local Storage: Room + DataStore
- Playback: Media3 (ExoPlayer) + MediaSession
- Navigation: Navigation Compose (type-safe routes)
- Images: Coil

## Requirements

- Android Studio (Ladybug or newer recommended)
- JDK 11+
- Android SDK:
  - `compileSdk = 36`
  - `minSdk = 26`
  - `targetSdk = 36`

## Getting Started

1. Clone the repository.
2. Configure your API key (required):

```properties
TUNEHUB_API_KEY=th_your_api_key_here
```

You can put it in `gradle.properties` or `local.properties`.

3. (Optional) Configure app update repository:

```properties
APP_UPDATE_REPO=owner/repository
```

4. (Recommended) Configure release signing for installable APK output:

```properties
RELEASE_STORE_FILE=C:/path/to/your/release-keystore.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

5. Build and run.

### Build Commands

Windows (PowerShell):

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat testDebugUnitTest
```

Build signed release APK (PowerShell):

```powershell
.\gradlew.bat ':app:assembleRelease' `
  '-PRELEASE_STORE_FILE=C:/path/to/your/release-keystore.jks' `
  '-PRELEASE_STORE_PASSWORD=your_store_password' `
  '-PRELEASE_KEY_ALIAS=your_key_alias' `
  '-PRELEASE_KEY_PASSWORD=your_key_password' `
  '-PRELEASE_SIGNING_REQUIRED=true'
```

macOS/Linux:

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
```

## Runtime Configuration

- API key can also be updated in-app from the **More** screen.
- The project includes update-related configuration through `APP_UPDATE_REPO`.

## CI Release Secrets

For `.github/workflows/release-update-manifest.yml`, configure these repository secrets:

- `RELEASE_TOKEN` -- create GitHub Release and upload APK asset
- `CHECK_UPDATE_PAT` -- write access token for `owner/check-update` repository
- `RELEASE_KEYSTORE_BASE64` -- Base64 of your release keystore file
- `RELEASE_STORE_PASSWORD` -- keystore password
- `RELEASE_KEY_ALIAS` -- signing key alias
- `RELEASE_KEY_PASSWORD` -- signing key password

Generate `RELEASE_KEYSTORE_BASE64` locally (PowerShell):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\path\to\release-keystore.jks')) | Set-Clipboard
```

## Auto Update Guide

Detailed local guide:

- `docs/auto-update-2.0-local-guide.md`

## Architecture Overview

The codebase is organized by responsibilities:

- `domain`: pure business models and repository interfaces
- `data`: repository implementations, remote orchestration, caching, parsing
- `core`: shared infrastructure (network, database, datastore, common types)
- `feature`: UI screens and ViewModels (home/search/library/player/playlist/discover/more)
- `media`: Media3 playback engine, session, service, queue/state handling
- `di`: Hilt modules and bindings

### Core Design Idea: Dispatch Engine

A template-driven dispatch layer is used for part of the online API integration, so behavior can be adjusted by server-provided method templates instead of hardcoding every endpoint flow in the client.

## Project Structure

```text
music/
├── app/
│   ├── src/main/java/com/music/myapplication/
│   │   ├── app/            # Application, Activity, navigation root
│   │   ├── core/           # Common, database, datastore, network
│   │   ├── data/           # Repository implementations + DTOs
│   │   ├── domain/         # Domain models + repository interfaces
│   │   ├── feature/        # Screens, UI state, ViewModels
│   │   ├── media/          # Playback service/session/state
│   │   ├── di/             # Dependency injection modules
│   │   └── ui/theme/       # Theme, tokens, styles
│   └── build.gradle.kts
├── gradle/libs.versions.toml
└── build.gradle.kts
```

## Testing

Current test coverage includes:

- Network/template transformation logic
- Repository-level online/recommendation logic
- Search and playlist detail ViewModel behavior

Run all unit tests:

```bash
./gradlew testDebugUnitTest
```

## Notes

- UI-facing texts are primarily Chinese in the current app implementation.
- The app uses platform-specific resolver and fallback logic for better playback success across sources.
- Database configuration currently uses destructive migration fallback in Room.

## Contributing

1. Keep architecture boundaries stable (`domain` interfaces first, implementations in `data`).
2. Prefer small, incremental refactors for large modules.
3. Run build/tests before opening a PR.
