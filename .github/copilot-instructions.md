# Tram Time Widget - Android Project

## Project Overview
Android home screen widget that displays real-time tram arrival times for configured stations.

## Tech Stack
- Kotlin
- Android SDK (minSdk 26, targetSdk 34)
- Jetpack libraries (WorkManager, Room, DataStore)
- Retrofit for network requests
- Kotlin Coroutines for async operations

## Project Structure
```
app/
├── src/main/
│   ├── java/com/widgettime/tram/
│   │   ├── TramWidgetProvider.kt      # Widget implementation
│   │   ├── TramWidgetConfigActivity.kt # Configuration activity
│   │   ├── data/
│   │   │   ├── TramRepository.kt       # Data repository
│   │   │   ├── TramApi.kt              # Retrofit API interface
│   │   │   └── models/                 # Data models
│   │   ├── worker/
│   │   │   └── WidgetUpdateWorker.kt   # Background updates
│   │   └── utils/
│   │       └── TimeUtils.kt            # Time formatting utilities
│   └── res/
│       ├── layout/
│       │   ├── widget_tram.xml         # Widget layout
│       │   └── activity_config.xml     # Config activity layout
│       ├── xml/
│       │   └── widget_info.xml         # Widget metadata
│       └── values/
│           └── strings.xml
```

## Build Commands
- Build: `./gradlew assembleDebug`
- Install: `./gradlew installDebug`
- Test: `./gradlew test`

## Configuration
Users can configure their stations of interest through the widget configuration activity that appears when adding the widget to the home screen.
