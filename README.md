# Tram Time Widget - Montpellier

An Android home screen widget that displays real-time tram arrival times for Montpellier's TAM public transportation system using GTFS-RT data.

## Features

- 🚊 Real-time tram arrival information from Montpellier TAM
- 📍 Configure multiple stations of interest
- 🔄 Automatic background updates (every 15 minutes)
- 📱 Resizable widget that fits your home screen
- 👆 Tap to refresh, tap station to reconfigure
- 🗺️ Uses official GTFS-RT data from transport.data.gouv.fr

## Screenshots

*Add screenshots here*

## Requirements

- Android 8.0 (API 26) or higher
- Internet connection for real-time data
- Located in Montpellier area

## Building the Project

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/widget-time.git
   cd widget-time
   ```

2. Open the project in Android Studio or build from command line:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on a connected device:
   ```bash
   ./gradlew installDebug
   ```

## Montpellier GTFS-RT Integration

### API Configuration

The app uses Montpellier's official GTFS-RT data from **transport.data.gouv.fr**:

- **Base URL**: `https://transport.data.gouv.fr/gtfs_rt/tam_montpellier/`
- **Format**: Protocol Buffers (GTFS-RT standard)
- **Endpoints**:
  - `/trip_updates` - Real-time vehicle arrivals and delays
  - `/vehicle_positions` - Current vehicle locations
  - `/service_alerts` - Service disruptions and alerts

### Supported Stations

The widget supports all main Montpellier TAM tram stations:

- **Line T1**: Mosson, Peyrou, Arceaux, Antigone, Occitanie, Gares Routières, Haltes-Gares
- **Line T2**: Saint-Jean, Bonne Nouvelle, Antigone, Occitanie, Saint-Roch, Gares Routières
- **Line T3**: Mosson, Jeu de Paume, Verdanelle, Sud-Ouest, Gares Routières

### Real-time Data

GTFS-RT provides:
- ✅ Trip updates with estimated arrival times
- ✅ Service alerts for disruptions
- ✅ Vehicle positions
- ✅ Schedule deviations and cancellations

## Project Structure

```
app/
├── src/main/
│   ├── java/com/widgettime/tram/
│   │   ├── TramWidgetProvider.kt      # Widget implementation
│   │   ├── TramWidgetConfigActivity.kt # Configuration activity
│   │   ├── StationAdapter.kt          # RecyclerView adapter
│   │   ├── MainActivity.kt            # Main app activity
│   │   ├── data/
│   │   │   ├── TramRepository.kt      # GTFS-RT data repository
│   │   │   ├── TramApi.kt             # Retrofit API interface
│   │   │   └── models/                # Data models
│   │   ├── worker/
│   │   │   └── WidgetUpdateWorker.kt  # Background updates
│   │   └── utils/
│   │       ├── TimeUtils.kt           # Time utilities
│   │       └── GtfsRtParser.kt        # GTFS-RT protocol parser
│   └── res/
│       ├── layout/                    # UI layouts
│       ├── xml/                       # Widget metadata
│       ├── drawable/                  # Icons and backgrounds
│       └── values/                    # Strings, colors, themes
```

## Development

### Tech Stack

- **Kotlin** - Primary programming language
- **Android Jetpack** - WorkManager, DataStore
- **Retrofit** - HTTP client for API requests
- **Protocol Buffers** - GTFS-RT data format
- **Coroutines** - Asynchronous programming

### Setting Up GTFS-RT Protobuf Parsing

To fully parse GTFS-RT protocol buffer data:

1. Add protobuf plugin to `build.gradle.kts`:
   ```kotlin
   id("com.google.protobuf") version "0.9.4"
   ```

2. Download `gtfs-realtime.proto` from [GTFS GitHub](https://github.com/google/transit/blob/master/realtime/proto/gtfs-realtime.proto)

3. Place it in `app/src/main/proto/`

4. Configure protobuf generation in `build.gradle.kts`:
   ```kotlin
   protobuf {
       protoc {
           artifact = "com.google.protobuf:protoc:3.24.4"
       }
       generateProtoTasks {
           all().each { task ->
               task.builtins {
                   java_lite {}
               }
           }
       }
   }
   ```

### Running Tests

```bash
./gradlew test           # Unit tests
./gradlew connectedCheck # Instrumented tests
```

## Montpellier TAM Resources

- **Official Website**: https://www.tam-voyages.com/
- **Data Portal**: https://transport.data.gouv.fr/datasets/tam-montpellier-gtfs
- **Real-time API**: https://transport.data.gouv.fr/gtfs_rt/tam_montpellier/

## License

MIT License - See [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## Support

For issues or questions related to:
- **TAM Service**: Contact [TAM Montpellier](https://www.tam-voyages.com/)
- **Data Platform**: Visit [transport.data.gouv.fr](https://transport.data.gouv.fr/)
- **App Development**: Create an issue on GitHub
