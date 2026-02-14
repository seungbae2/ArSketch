# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ArSketch is a real-time AR drawing Android app that streams augmented reality sketches to web viewers via LiveKit. Built with Kotlin, Jetpack Compose, and ARCore. The project follows multi-module Clean Architecture with Gradle Kotlin DSL and version catalogs.

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

# Build specific module
./gradlew :core:domain:assemble
./gradlew :feature:host:assemble
```

## Testing

```bash
# Run all unit tests
./gradlew test

# Run unit tests for debug variant
./gradlew testDebugUnitTest

# Run tests for a specific module
./gradlew :core:domain:test

# Run a single test class
./gradlew test --tests "com.sb.arsketch.domain.usecase.stroke.AddPointToStrokeUseCaseTest"

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
- **Min SDK**: 26 (Android 8.0)
- **Target/Compile SDK**: 36
- **JVM Target**: 11
- **Package**: `com.sb.arsketch`
- **DI**: Hilt 2.56
- **Navigation**: Navigation Compose 2.9

### Multi-Module Structure

```
:app                        ← Application shell, DI wiring, Navigation
├── :core:domain            ← Domain models, UseCases (pure Kotlin, no Android deps)
├── :core:ar                ← ARCore session, renderer, drawing controller
├── :core:streaming-api     ← Streaming interfaces & state definitions
├── :core:streaming         ← LiveKit implementation (foreground service)
├── :core:ui                ← Compose theme (colors, typography)
├── :feature:connect        ← Connection screen (URL + token input)
├── :feature:host           ← Host screen (AR drawing + streaming)
└── :feature:viewer         ← Viewer screen (remote video + stroke overlay)
```

### Module Dependencies

```
feature:host ────▶ core:domain, core:ar, core:streaming-api, core:ui
feature:viewer ──▶ core:domain, core:streaming-api, core:ui
feature:connect ─▶ core:domain, core:ui

core:streaming ──▶ core:streaming-api, core:domain
core:ar ─────────▶ core:domain

app ─────────────▶ all modules (assembly + DI)
```

### Key Directories

| Module | Path | Description |
|--------|------|-------------|
| `app` | `app/src/main/java/com/sb/arsketch/` | Application, MainActivity, DI modules, NavGraph |
| `core:domain` | `core/domain/src/main/java/com/sb/arsketch/domain/` | `model/` (Point3D, Stroke, StrokeEvent, BrushSettings, DrawingMode) + `usecase/stroke/` |
| `core:ar` | `core/ar/src/main/java/com/sb/arsketch/ar/` | `core/` (ARSessionManager, DrawingController) + `renderer/` + `util/` + `geometry/` |
| `core:streaming-api` | `core/streaming-api/src/main/java/com/sb/arsketch/streaming/` | `api/` interfaces (HostStreamingController, HostStreamingSession, ViewerStreamingClient) + state models |
| `core:streaming` | `core/streaming/src/main/java/com/sb/arsketch/streaming/` | HybridStreamingService, HostStreamingSessionImpl, ARFrameCapturer, ViewerConnectionManager |
| `core:ui` | `core/ui/src/main/java/com/sb/arsketch/ui/theme/` | Color, Theme, Type |
| `feature:connect` | `feature/connect/src/main/java/com/sb/arsketch/presentation/connect/` | ConnectScreen/Route/ViewModel/UiState/Action/Event |
| `feature:host` | `feature/host/src/main/java/com/sb/arsketch/presentation/host/` | HostScreen/Route/ViewModel/UiState/Action/Event + `component/` |
| `feature:viewer` | `feature/viewer/src/main/java/com/sb/arsketch/presentation/viewer/` | ViewerScreen/Route/ViewModel/UiState/Action/Event + `component/` |
| `web-viewer` | `web-viewer/src/` | TypeScript + Vite browser viewer |

### Presentation Layer Pattern

Each feature module follows a consistent pattern:
- **Route** — Navigation entry point, collects ViewModel events via `LaunchedEffect`
- **Screen** — Stateless Composable, receives `UiState` and `onAction` callback
- **ViewModel** — Holds `StateFlow<UiState>`, processes `Action`, emits `Event` via `Channel`
- **Action** — Sealed class for user interactions
- **Event** — Sealed class for one-time side effects (navigation, snackbar)

### Dependencies (via Version Catalog)

Key dependencies managed in `gradle/libs.versions.toml`:
- Kotlin 2.2, Compose BOM 2026.01.01
- ARCore 1.52, LiveKit Android SDK 2.23.3
- Hilt 2.56, Navigation Compose 2.9.7
- kotlinx-serialization 1.10 (JSON over DataChannel)
- Timber for logging
- Testing: JUnit4, MockK, Turbine, kotlinx-coroutines-test
