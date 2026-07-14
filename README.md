# Wi-Fi Spatial Mapping & Analysis

An Android application for mapping Wi-Fi signal coverage and analyzing throughput performance. The app allows you to place floor plan markers, collect signal metrics alongside compass orientations, run speed tests, and perform statistical analysis (ANOVA, linear regression, and Tukey's HSD) directly on-device.

## Features

- **Spatial Data Collection**: Record signal strength (dBm) and throughput at specific coordinates relative to floor plan markers.
- **Sensor Integration**: Uses the device compass to track orientation during measurements.
- **Speed Tests**: Benchmarks download and upload speeds using OkHttp.
- **Statistical Analysis**: 
  - Linear regression (OLS) to relate signal power to speed.
  - Two-Way ANOVA to test the effects of floor level and location.
  - Tukey's HSD post-hoc comparisons.
- **Data Import/Export**: Save and load datasets as CSV or JSON.
- **Simulation Mode**: Run simulated measurements for offline testing.

## Project Structure

- `app/src/main/java/.../wifispatial/`
  - `data/`: Room entities and database setup.
  - `network/`: Speed test engine.
  - `sensor/`: Compass tracking.
  - `stats/`: Custom math and statistics using Apache Commons Math3.
  - `ui/`: Compose layouts, navigation, and theme.
  - `viewmodel/`: State management.

## Setup

### Prerequisites
- JDK 17
- Android Studio (Ladybug or newer)
- Android SDK 34+

### Building & Running
Open the project in Android Studio, let Gradle sync complete, and run the app on a device or emulator. 

Alternatively, build via the command line:
```bash
./gradlew assembleDebug
```

### Running Tests
- **Unit Tests**: `./gradlew test`
- **Instrumented Tests**: `./gradlew connectedAndroidTest`
