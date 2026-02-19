# ArSketch

Real-time AR drawing app that streams augmented reality sketches to web viewers via LiveKit.

Draw on surfaces or in mid-air using ARCore, and share your creation live with anyone through a browser.

<!-- TODO: Add demo GIF/screenshot here -->

## Features

- **Surface Drawing** — Draw on detected planes using ARCore raycasting
- **Air Drawing** — Sketch freely in 3D space at adjustable depth
- **Brush Settings** — 8 colors and 3 thickness levels
- **Undo / Redo / Clear** — Full stroke history management
- **Real-time Streaming** — AR camera feed streamed via LiveKit (WebRTC)
- **Web Viewer** — Watch the AR stream and draw remotely from any browser
- **Bidirectional Interaction** — Web viewers can send touch events back to the host

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Android UI | Kotlin 2.2, Jetpack Compose (BOM 2026.01), Material3 |
| AR | ARCore 1.52 |
| Streaming | LiveKit Android SDK 2.23.3 (WebRTC SFU) |
| DI | Hilt 2.56 |
| Architecture | Multi-module Clean Architecture, MVVM |
| Navigation | Navigation Compose 2.9 |
| Web Viewer | TypeScript, Vite, livekit-client |
| Serialization | kotlinx-serialization 1.10 (JSON over DataChannel) |

## Architecture

### Multi-Module Structure

```
:app                        ← Application shell, DI wiring, Navigation
├── :core:domain            ← Domain models, UseCases (pure Kotlin)
├── :core:ar                ← ARCore session, renderer, drawing controller
├── :core:streaming-api     ← Streaming interfaces & state definitions
├── :core:streaming         ← LiveKit implementation (foreground service)
├── :core:ui                ← Compose theme (colors, typography)
├── :feature:connect        ← Connection screen (URL + token input)
├── :feature:host           ← Host screen (AR drawing + streaming)
└── :feature:viewer         ← Viewer screen (remote video + stroke overlay)
```

### Module Dependency Graph

```
feature:host ────▶ core:domain, core:ar, core:streaming-api, core:ui
feature:viewer ──▶ core:domain, core:streaming-api, core:ui
feature:connect ─▶ core:domain, core:ui

core:streaming ──▶ core:streaming-api, core:domain
core:ar ─────────▶ core:domain

app ─────────────▶ all modules (assembly + DI)
```

### System Architecture

```
┌─────────────────── Android Host ───────────────────┐
│                                                     │
│  Presentation          Domain           Streaming   │
│  ┌───────────┐   ┌──────────────┐   ┌────────────┐ │
│  │HostScreen │──▶│ UseCases     │   │ LiveKit    │ │
│  │ ViewModel │   │ Models       │   │ Room       │ │
│  │ Compose UI│   │ StrokeEvent  │   │            │ │
│  └───────────┘   └──────────────┘   └─────┬──────┘ │
│        │                                   │        │
│  ┌─────▼─────┐                      ┌─────▼──────┐ │
│  │ ARCore    │───PixelCopy─────────▶│ Video Track│ │
│  │ Renderer  │                      │ DataChannel│ │
│  └───────────┘                      └─────┬──────┘ │
└───────────────────────────────────────────┼────────┘
                                            │ WebRTC
                                    ┌───────▼───────┐
                                    │  LiveKit SFU  │
                                    └───────┬───────┘
                                            │
┌───────────────────────────────────────────┼────────┐
│                Web Viewer                  │        │
│  ┌────────────┐                ┌──────────▼─────┐  │
│  │ <video>    │                │ livekit-client │  │
│  │ AR Stream  │                │ DataChannel    │  │
│  └────────────┘                └────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**Data flow:**
- **Video**: ARCore SurfaceView (with strokes) → PixelCopy → BitmapFrameCapturer → LiveKit VideoTrack → Web `<video>`
- **Remote touch**: Web touch/mouse → DataChannel (topic: `remote_touch`) → Host DrawingController

## Prerequisites

- **Android Studio** (latest stable)
- **Android SDK** 26+ (minSdk 26, compileSdk 36)
- **ARCore-supported device** ([check compatibility](https://developers.google.com/ar/devices))
- **Node.js** 18+ (for web viewer)
- **LiveKit account** — [livekit.io](https://livekit.io) (free tier available)
- **livekit-cli** — for generating access tokens

### Install livekit-cli

```bash
# macOS
brew install livekit-cli

# or download from https://github.com/livekit/livekit-cli/releases
```

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-username/ArSketch.git
cd ArSketch
```

### 2. Generate LiveKit tokens

You need two tokens: one for the **host** (Android, can publish) and one for the **viewer** (Web, can subscribe and publish data).

```bash
# Host token (Android app — publishes video + data)
livekit-cli create-token \
  --api-key YOUR_API_KEY \
  --api-secret YOUR_API_SECRET \
  --join --room arsketch \
  --identity host \
  --name "AR Host" \
  --valid-for 8760h \
  --grant '{"canPublish":true,"canPublishData":true,"canSubscribe":true}'

# Viewer token (Web — subscribes to video, publishes data)
livekit-cli create-token \
  --api-key YOUR_API_KEY \
  --api-secret YOUR_API_SECRET \
  --join --room arsketch \
  --identity viewer \
  --name "Web Viewer" \
  --valid-for 8760h \
  --grant '{"canPublish":false,"canPublishData":true,"canSubscribe":true}'
```

### 3. Set up the Android app

Create `local.properties` in the project root (or add to existing):

```properties
LIVEKIT_URL=wss://your-project.livekit.cloud
LIVEKIT_HOST_TOKEN=your_host_token_here
```

Build and install:

```bash
./gradlew assembleDebug
# Install via Android Studio or:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Set up the Web Viewer

```bash
cd web-viewer
npm install
```

Create `.env.local`:

```
VITE_LIVEKIT_URL=wss://your-project.livekit.cloud
VITE_LIVEKIT_TOKEN=your_viewer_token_here
```

Start the dev server:

```bash
npm run dev
```

## Running a Demo

1. **Start the Android app** on an ARCore-supported device
2. **Tap "Connect"** — the app connects to the LiveKit room as host
3. **Open the web viewer** at `http://localhost:5173` in a browser
4. **Enter your LiveKit URL and token**, then click Connect
5. **Point your phone** at a flat surface — wait for plane detection (tracking indicator turns green)
6. **Draw on your phone** — strokes appear in AR and stream to the web viewer in real-time
7. **Draw on the web viewer** — touch/mouse strokes are sent back to the AR host

### Tips

- Switch between **Surface** and **Air** drawing modes using the toggle button
- Adjust **air drawing depth** with the depth slider
- Use the **color picker** and **thickness selector** to customize your brush
- **Undo/Redo** buttons are in the action toolbar

## Project Structure

```
ArSketch/
├── app/                                    # Application shell
│   └── src/main/java/com/sb/arsketch/
│       ├── ArSketchApplication.kt          # Hilt application
│       ├── MainActivity.kt                 # Single activity entry point
│       ├── di/
│       │   ├── AppConfigModule.kt          # App-level Hilt config
│       │   └── StreamingModule.kt          # Streaming DI bindings
│       └── presentation/navigation/
│           └── ArSketchNavGraph.kt         # Screen navigation
│
├── core/
│   ├── domain/                             # Pure Kotlin domain layer
│   │   └── src/main/java/.../domain/
│   │       ├── model/                      # Point3D, Stroke, BrushSettings, StrokeEvent,
│   │       │                               # DrawingMode, RoomConnectionConfig, RemoteTouchEvent
│   │       └── usecase/stroke/             # Create, AddPoint, Undo, Redo, ClearAll
│   │
│   ├── ar/                                 # ARCore integration
│   │   └── src/main/java/.../ar/
│   │       ├── core/                       # ARSessionManager, DrawingController,
│   │       │                               # AnchorManager, ARGLSurfaceView
│   │       ├── renderer/                   # ARRenderer, StrokeRenderer, PlaneRenderer,
│   │       │                               # BackgroundRenderer, ShaderUtil
│   │       ├── util/                       # TouchToWorldConverter, AirDrawingProjector,
│   │       │                               # HitTestHelper
│   │       └── geometry/                   # LineStripMesh
│   │
│   ├── streaming-api/                      # Streaming abstractions
│   │   └── src/main/java/.../streaming/api/
│   │       ├── HostStreamingSession.kt     # Host interface (connect, publish, surface)
│   │       ├── ViewerStreamingClient.kt    # Viewer interface (connect, disconnect)
│   │       ├── StrokeEventSource.kt        # Stroke event stream interface
│   │       └── ConnectionState.kt          # Unified state: Idle, Connecting, Connected, Error
│   │
│   ├── streaming/                          # LiveKit implementation
│   │   └── src/main/java/.../streaming/
│   │       ├── HybridStreamingService.kt   # Foreground service (video + data)
│   │       ├── HostStreamingSessionImpl.kt # Service binding lifecycle manager
│   │       ├── ARFrameCapturer.kt          # PixelCopy → BitmapFrameCapturer
│   │       ├── StrokeEventReceiver.kt      # DataChannel stroke parsing
│   │       ├── ViewerConnectionManager.kt  # Viewer-side LiveKit connection
│   │       └── StreamingConstants.kt       # Internal constants (data topics)
│   │
│   └── ui/                                 # Compose theme
│       └── src/main/java/.../ui/theme/
│           ├── Color.kt
│           ├── Theme.kt
│           └── Type.kt
│
├── feature/
│   ├── connect/                            # Connection screen
│   │   └── src/main/java/.../connect/
│   │       ├── ConnectScreen.kt            # URL + token input UI
│   │       ├── ConnectRoute.kt             # Navigation wiring
│   │       ├── ConnectViewModel.kt
│   │       ├── ConnectUiState.kt
│   │       ├── ConnectAction.kt
│   │       └── ConnectEvent.kt
│   │
│   ├── host/                               # Host screen (AR drawing)
│   │   └── src/main/java/.../host/
│   │       ├── HostScreen.kt               # AR view + brush toolbar
│   │       ├── HostRoute.kt                # Navigation wiring
│   │       ├── HostViewModel.kt
│   │       ├── HostUiState.kt
│   │       ├── HostAction.kt
│   │       ├── HostEvent.kt
│   │       └── component/                  # ColorPicker, ThicknessSelector,
│   │                                       # DepthSlider, DrawingModeToggle,
│   │                                       # ActionToolbar, BrushToolbar,
│   │                                       # TrackingStatusIndicator,
│   │                                       # PlaneVisibilityToggle
│   │
│   └── viewer/                             # Viewer screen (remote video)
│       └── src/main/java/.../viewer/
│           ├── ViewerScreen.kt             # Video + stroke overlay
│           ├── ViewerRoute.kt              # Navigation wiring
│           ├── ViewerViewModel.kt
│           ├── ViewerUiState.kt
│           ├── ViewerAction.kt
│           ├── ViewerEvent.kt
│           └── component/
│               └── StrokeOverlay.kt
│
├── web-viewer/                             # Browser-based viewer
│   ├── src/
│   │   ├── main.ts                         # Entry point
│   │   ├── livekit-connection.ts           # Room connection, video subscription
│   │   ├── drawing-input.ts                # Mouse/touch input → remote drawing
│   │   ├── remote-touch.ts                 # RemoteTouchEvent type definitions
│   │   └── ui.ts                           # UI controls
│   ├── index.html
│   └── package.json
│
├── gradle/libs.versions.toml              # Centralized dependency versions
├── settings.gradle.kts                    # Module declarations
└── CLAUDE.md
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| **Black video in web viewer** | Ensure `startCapture()` is called after `createVideoTrack()`. Check logcat for LiveKit errors. |
| **No plane detection** | Point camera at a textured, well-lit flat surface. Move the device slowly. |
| **"ARCore not installed"** | Install Google Play Services for AR from the Play Store. |
| **Web viewer won't connect** | Verify `.env.local` has correct LiveKit URL and a valid token. Check browser console for errors. |
| **Strokes not appearing on web** | Strokes are rendered in the AR video feed. Confirm both host and viewer are in the same room and video track is publishing. |
| **Token expired** | Regenerate tokens with `livekit-cli`. Use `--valid-for 8760h` for long-lived tokens. |
| **Build fails with missing `LIVEKIT_URL`** | Ensure `local.properties` contains both `LIVEKIT_URL` and `LIVEKIT_HOST_TOKEN`. |

## Build Commands

```bash
# Android
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew compileDebugKotlin     # Compile check
./gradlew test                   # Unit tests
./gradlew lint                   # Lint check

# Web Viewer
cd web-viewer
npm run dev                      # Dev server
npm run build                    # Production build
npm run preview                  # Preview production build
```

## License

This project is for portfolio/demo purposes.
