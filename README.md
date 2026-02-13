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
| Android UI | Kotlin, Jetpack Compose, Material3 |
| AR | ARCore 1.52 |
| Streaming | LiveKit Android SDK 2.23.3 (WebRTC SFU) |
| DI | Hilt 2.56 |
| Architecture | Clean Architecture, MVVM |
| Web Viewer | TypeScript, Vite, livekit-client |
| Serialization | kotlinx-serialization (JSON over DataChannel) |

## Architecture

```
┌─────────────────── Android Host ───────────────────┐
│                                                     │
│  Presentation          Domain           Streaming   │
│  ┌───────────┐   ┌──────────────┐   ┌────────────┐ │
│  │HostScreen │──▶│ UseCases     │   │ LiveKit    │ │
│  │ViewModel  │   │ Models       │   │ Room       │ │
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
│  ┌────────────┐  ┌──────────┐  ┌──────────▼─────┐  │
│  │ <video>    │  │ Canvas   │  │ livekit-client │  │
│  │ AR Stream  │  │ Overlay  │  │ DataChannel    │  │
│  └────────────┘  └──────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────┘
```

**Data flow:**
- **Video**: ARCore GLSurfaceView → PixelCopy → BitmapFrameCapturer → LiveKit VideoTrack → Web `<video>`
- **Strokes**: StrokeEvent → JSON → DataChannel (topic: `ar_drawing`) → Web Canvas overlay
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
├── app/src/main/java/com/sb/arsketch/
│   ├── ar/
│   │   ├── core/           # ARSessionManager, DrawingController, AnchorManager
│   │   ├── renderer/       # ARRenderer, StrokeRenderer, PlaneRenderer
│   │   ├── util/           # TouchToWorldConverter, AirDrawingProjector
│   │   └── geometry/       # LineStripMesh
│   ├── domain/
│   │   ├── model/          # Point3D, Stroke, BrushSettings, StrokeEvent
│   │   └── usecase/stroke/ # Create, AddPoint, Undo, Redo, ClearAll
│   ├── presentation/
│   │   ├── host/           # Host screen (AR drawing + streaming)
│   │   ├── viewer/         # Viewer screen (receive strokes)
│   │   ├── connect/        # Connection screen (URL + token input)
│   │   └── navigation/     # NavGraph
│   ├── streaming/          # HybridStreamingService, ARFrameCapturer
│   └── ui/theme/           # Compose theme
├── web-viewer/
│   ├── src/
│   │   ├── main.ts              # Entry point
│   │   ├── livekit-connection.ts # Room connection, subscriptions
│   │   ├── stroke-processor.ts   # Parse stroke events
│   │   ├── stroke-renderer.ts    # Canvas rendering
│   │   ├── drawing-input.ts      # Mouse/touch input
│   │   └── types.ts              # TypeScript interfaces
│   ├── index.html
│   └── package.json
├── gradle/libs.versions.toml    # Dependency versions
└── CLAUDE.md
```

## Troubleshooting

| Problem | Solution |
|---------|----------|
| **Black video in web viewer** | Ensure `startCapture()` is called after `createVideoTrack()`. Check logcat for LiveKit errors. |
| **No plane detection** | Point camera at a textured, well-lit flat surface. Move the device slowly. |
| **"ARCore not installed"** | Install Google Play Services for AR from the Play Store. |
| **Web viewer won't connect** | Verify `.env.local` has correct LiveKit URL and a valid token. Check browser console for errors. |
| **Strokes not appearing on web** | Confirm both host and viewer are in the same room. Check DataChannel topic is `ar_drawing`. |
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
