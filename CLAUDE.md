# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ArSketch is an Android application built with Kotlin and Jetpack Compose. The project uses Gradle with Kotlin DSL and version catalogs for dependency management.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Check for compilation errors
./gradlew compileDebugKotlin
```

## Testing

```bash
# Run all unit tests
./gradlew test

# Run unit tests for debug variant
./gradlew testDebugUnitTest

# Run a single test class
./gradlew test --tests "com.sb.arsketch.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

## Linting and Code Quality

```bash
# Run Android lint
./gradlew lint

# Run lint for debug variant
./gradlew lintDebug
```

## Architecture

- **UI Framework**: Jetpack Compose with Material3
- **Min SDK**: 24 (Android 7.0)
- **Target/Compile SDK**: 36
- **JVM Target**: 11
- **Package**: `com.sb.arsketch`

### Project Structure

- `app/src/main/java/com/sb/arsketch/` - Main application code
  - `MainActivity.kt` - Single activity entry point
  - `ui/theme/` - Compose theme configuration (colors, typography, theme)
- `app/src/test/` - Local unit tests (JUnit)
- `app/src/androidTest/` - Instrumented tests (Espresso)
- `gradle/libs.versions.toml` - Centralized dependency versions

### Dependencies (via Version Catalog)

Key dependencies are managed in `gradle/libs.versions.toml`:
- Compose BOM 2024.09.00 for consistent Compose versions
- AndroidX Core KTX, Lifecycle, Activity Compose
- Material3 for UI components
- JUnit4 and Espresso for testing
