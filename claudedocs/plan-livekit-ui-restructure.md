# Implementation Plan: ArSketch LiveKit UI Restructure

## Goal
Transform ArSketch from a drawing-focused app into a LiveKit WebRTC sample-style app with Host/Viewer roles, removing offline session management and restructuring navigation around Room connection.

## New App Flow
```
[ConnectScreen]                    ← LiveKit sample style
  Server URL (hardcoded, editable)
  Token input
  Role: Host / Viewer toggle
  Connect button
        ↓
[HostScreen]                       ← Host role
  Fullscreen AR drawing
  Bottom mini control panel
  Streaming status + participant count
  Disconnect button
        ↓
[ViewerScreen]                     ← Viewer role
  Remote camera video (fullscreen)
  DataChannel stroke overlay (2D Canvas)
  Disconnect button
```

## Architecture Decisions

### Viewer Rendering Strategy
- **Use Compose Canvas** (not OpenGL) for viewer stroke overlay
- Remote camera video via LiveKit `VideoTrackView` composable
- DataChannel strokes rendered as 2D lines on Canvas overlay
- Rationale: Viewer doesn't need AR/3D rendering - 2D projection is sufficient and much simpler

### LiveKit Components Library
- Add `io.livekit:livekit-android-compose-components:2.1.2` for `RoomScope`, `VideoTrackView`, `rememberTracks`
- Use alongside existing `io.livekit:livekit-android:2.23.3`

### Streaming Service Refactor
- Keep `HybridStreamingService` for Host (camera publish + DataChannel publish)
- Create new `ViewerConnectionManager` for Viewer (subscribe video + receive DataChannel)
- Both share common `RoomConnectionConfig` data class

---

## Phase 1: Remove Offline Session Features
**Goal**: Clean up session-related code to simplify the codebase

### Step 1.1: Delete session-related files
**Files to DELETE:**
- `presentation/screen/sessions/SessionListScreen.kt`
- `presentation/screen/sessions/SessionListViewModel.kt` (if exists)
- `presentation/component/SaveSessionDialog.kt`
- `domain/usecase/session/CreateSessionUseCase.kt`
- `domain/usecase/session/DeleteSessionUseCase.kt`
- `domain/usecase/session/GetAllSessionsUseCase.kt`
- `domain/usecase/session/LoadSessionUseCase.kt`
- `domain/usecase/session/SaveSessionUseCase.kt`
- `domain/usecase/stroke/SaveStrokeUseCase.kt`
- `domain/repository/SessionRepository.kt`
- `domain/model/DrawingSession.kt`
- `data/repository/SessionRepositoryImpl.kt`
- `data/local/db/SessionDao.kt`
- `data/local/entity/SessionEntity.kt`
- `data/mapper/EntityMapper.kt` (if only used for sessions)

### Step 1.2: Simplify database
**Edit** `data/local/db/ArSketchDatabase.kt`:
- Remove `SessionEntity` from `@Database` entities
- Remove `sessionDao()` method
- Increment database version or use `fallbackToDestructiveMigration()`

**Edit** `data/local/entity/StrokeEntity.kt`:
- Remove `sessionId` foreign key (strokes no longer tied to sessions)
- Or: Remove `StrokeEntity` entirely if strokes are only in-memory now

### Step 1.3: Clean up DI modules
**Edit** `di/RepositoryModule.kt`:
- Remove `bindSessionRepository()` binding

**Edit** `di/DataModule.kt`:
- Remove `provideSessionDao()` if deleted

### Step 1.4: Clean up DrawingViewModel
**Edit** `presentation/screen/drawing/DrawingViewModel.kt`:
- Remove session-related UseCase injections (CreateSession, SaveSession, LoadSession)
- Remove session-related action handlers (ShowSaveDialog, DismissSaveDialog, UpdateSessionName, SaveSession, StartNewSession, LoadSession)
- Remove session-related state fields

**Edit** `presentation/screen/drawing/DrawingAction.kt`:
- Remove: ShowSaveDialog, DismissSaveDialog, UpdateSessionName, SaveSession, StartNewSession, LoadSession

**Edit** `presentation/state/DrawingUiState.kt`:
- Remove: showSaveDialog, sessionName fields

**Edit** `presentation/screen/drawing/DrawingEvent.kt`:
- Remove: SessionSaved, SessionLoaded

### Step 1.5: Clean up UI components
**Edit** `presentation/component/ActionToolbar.kt`:
- Remove Save and Sessions buttons (keep Undo/Redo/Clear)

**Verify**: Build compiles after all removals (`./gradlew compileDebugKotlin`)

---

## Phase 2: Create ConnectScreen (LiveKit Sample Style)
**Goal**: Replace HomeScreen with a LiveKit-style connection screen

### Step 2.1: Create RoomConnectionConfig model
**Create** `domain/model/RoomConnectionConfig.kt`:
```kotlin
data class RoomConnectionConfig(
    val serverUrl: String,
    val token: String,
    val role: RoomRole
)

enum class RoomRole {
    HOST,
    VIEWER
}
```

### Step 2.2: Create ConnectViewModel
**Create** `presentation/screen/connect/ConnectViewModel.kt`:
- State: serverUrl (hardcoded default), token, role (HOST/VIEWER), isConnecting, error
- Actions: UpdateUrl, UpdateToken, SetRole, Connect, ClearError
- On Connect → validate inputs → navigate with config

### Step 2.3: Create ConnectScreen UI
**Create** `presentation/screen/connect/ConnectScreen.kt`:
LiveKit sample app style:
- App title/logo at top
- Server URL text field (pre-filled with hardcoded URL)
- Token text field
- Role toggle (Host / Viewer) - SegmentedButton or toggle chips
- Connect button (full width)
- Error display if connection fails
- Material3 styling consistent with existing theme

### Step 2.4: Delete old HomeScreen
**Delete** `presentation/screen/home/HomeScreen.kt`

### Step 2.5: Update navigation
**Edit** `presentation/navigation/ArSketchNavGraph.kt`:
- Replace all routes with: CONNECT, HOST, VIEWER
- Remove: HOME, DRAWING, STREAMING, DRAWING_WITH_SESSION, SESSION_LIST
- ConnectScreen passes RoomConnectionConfig to Host/Viewer via navigation args
- Add serializable route classes for type-safe navigation

### Step 2.6: Update MainActivity
**Edit** `MainActivity.kt`:
- Navigation now starts at ConnectScreen
- AR components still injected but only passed to HostScreen route

**Verify**: Build compiles, app launches to ConnectScreen

---

## Phase 3: Refactor Host Screen
**Goal**: Transform DrawingScreen into HostScreen with fullscreen AR + mini controls

### Step 3.1: Create HostViewModel
**Create** `presentation/screen/host/HostViewModel.kt`:
- Merge drawing logic from DrawingViewModel
- Remove session logic (already removed in Phase 1)
- Add: RoomConnectionConfig as SavedStateHandle arg
- Auto-connect to LiveKit room on init using config
- Expose: drawingState, streamingState, participantCount

### Step 3.2: Create HostScreen UI
**Create** `presentation/screen/host/HostScreen.kt`:
Layout:
```
┌─────────────────────────┐
│     AR GLSurfaceView    │  ← Fullscreen
│     (camera + drawing)  │
│                         │
│                         │
├─────────────────────────┤
│ [🔴 LIVE] [👥 2]       │  ← Status bar (streaming state + participants)
│ [Mode] [Brush] [Undo]  │  ← Mini toolbar
│           [Disconnect]  │  ← Disconnect button
└─────────────────────────┘
```
- Reuse existing components: DrawingModeToggle, ColorPicker, ThicknessSelector, TrackingStatusIndicator
- New: Compact bottom sheet or bar with streaming info
- Disconnect button triggers navigation back to ConnectScreen

### Step 3.3: Create HostRoute
**Create** `presentation/screen/host/HostRoute.kt`:
- Camera permission handling (from existing DrawingRoute)
- AR session lifecycle management
- GLSurfaceView creation
- DrawingController callback setup
- Event collection

### Step 3.4: Refactor HybridStreamingService
**Edit** `streaming/HybridStreamingService.kt`:
- Accept `RoomConnectionConfig` instead of separate url/token
- Add participant count tracking via room events
- Expose `participantCount: StateFlow<Int>`

### Step 3.5: Delete old drawing screen files
**Delete** (after HostScreen is working):
- `presentation/screen/drawing/DrawingScreen.kt`
- `presentation/screen/drawing/DrawingRoute.kt`
- `presentation/screen/drawing/DrawingViewModel.kt`
- `presentation/screen/drawing/DrawingAction.kt`
- `presentation/screen/drawing/DrawingEvent.kt`

**Verify**: Build compiles, Host flow works: Connect → HostScreen → AR drawing + streaming

---

## Phase 4: Create Viewer Screen
**Goal**: Build viewer that receives remote video + DataChannel strokes

### Step 4.1: Add LiveKit Compose Components dependency
**Edit** `gradle/libs.versions.toml`:
```toml
[versions]
livekit-components = "2.1.2"

[libraries]
livekit-components-compose = { group = "io.livekit", name = "livekit-android-compose-components", version.ref = "livekit-components" }
```

**Edit** `app/build.gradle.kts`:
```kotlin
implementation(libs.livekit.components.compose)
```

**Edit** `settings.gradle.kts` (if needed):
- Add `maven { url = uri("https://jitpack.io") }` to repositories

### Step 4.2: Create ViewerConnectionManager
**Create** `streaming/ViewerConnectionManager.kt`:
```kotlin
class ViewerConnectionManager {
    private var room: Room? = null

    val connectionState: StateFlow<ConnectionState>
    val remoteVideoTrack: StateFlow<VideoTrack?>
    val receivedStrokes: StateFlow<List<ViewerStroke>>
    val participantCount: StateFlow<Int>

    suspend fun connect(config: RoomConnectionConfig)
    fun disconnect()
}
```
- Connects to LiveKit room (no camera/mic publish)
- Subscribes to remote video track via `RoomEvent.TrackSubscribed`
- Receives DataChannel messages on topic `"ar_drawing"`
- Deserializes `StrokeEvent` JSON → reconstructs `ViewerStroke` list
- Tracks participant count

### Step 4.3: Create ViewerStroke model
**Create** `domain/model/ViewerStroke.kt`:
```kotlin
data class ViewerStroke(
    val id: String,
    val points: List<Offset>,  // 2D screen coordinates (projected)
    val color: Color,
    val thickness: Float,
    val isComplete: Boolean
)
```

### Step 4.4: Create StrokeEventReceiver
**Create** `streaming/StrokeEventReceiver.kt`:
- Receives raw DataChannel bytes → deserializes to `StrokeEvent`
- Maintains stroke reconstruction state machine:
  - `Started` → create new stroke entry
  - `PointAdded` → append point to active stroke
  - `Ended` → mark stroke complete
  - `Deleted` → remove stroke by ID
  - `AllCleared` → clear all strokes
- Projects 3D Point3D to 2D Offset (simple x,y projection for now)
- Exposes `strokes: StateFlow<List<ViewerStroke>>`

### Step 4.5: Create ViewerViewModel
**Create** `presentation/screen/viewer/ViewerViewModel.kt`:
- Injects `ViewerConnectionManager`
- State: connectionState, strokes, participantCount, error
- Actions: Disconnect
- Auto-connects on init with RoomConnectionConfig from SavedStateHandle

### Step 4.6: Create StrokeOverlay composable
**Create** `presentation/component/StrokeOverlay.kt`:
- Compose Canvas overlay that draws received strokes
- Draws each ViewerStroke as a polyline with color and thickness
- Transparent background, positioned over video

### Step 4.7: Create ViewerScreen UI
**Create** `presentation/screen/viewer/ViewerScreen.kt`:
Layout:
```
┌─────────────────────────┐
│   Remote Video Feed     │  ← LiveKit VideoTrackView (fullscreen)
│   ┌─────────────────┐   │
│   │ Stroke Overlay   │  │  ← Canvas overlay with received strokes
│   │ (2D rendering)   │  │
│   └─────────────────┘   │
│                         │
├─────────────────────────┤
│ [🟢 Connected] [👥 2]  │  ← Status bar
│           [Disconnect]  │  ← Disconnect button
└─────────────────────────┘
```

Options:
- A) Use `RoomScope` + `VideoTrackView` from livekit-compose-components (simpler)
- B) Manual room management + TextureView (more control)

**Recommendation**: Option A with `RoomScope` for simplicity

### Step 4.8: Create ViewerRoute
**Create** `presentation/screen/viewer/ViewerRoute.kt`:
- Permission handling (INTERNET only, no CAMERA needed for viewer)
- Lifecycle management for room connection
- Event collection

### Step 4.9: Wire up navigation
**Edit** `presentation/navigation/ArSketchNavGraph.kt`:
- Add VIEWER route with ViewerScreen
- Pass RoomConnectionConfig from ConnectScreen

### Step 4.10: Create DI module for viewer
**Edit** `di/` - Add viewer dependencies:
- Provide `ViewerConnectionManager`
- Provide `StrokeEventReceiver`

**Verify**: Full flow works: Connect as Viewer → see remote video + strokes

---

## Phase 5: Polish & Integration
**Goal**: Final cleanup and testing

### Step 5.1: Clean up unused components
- Review `presentation/component/` - remove any unused components
- Remove `streaming/StreamingControls.kt` if replaced by HostScreen inline controls
- Remove `domain/service/RemoteDrawingService.kt` if replaced by ViewerConnectionManager
- Clean up unused imports across all files

### Step 5.2: Update AndroidManifest.xml
- Verify permissions are correct for both roles
- Viewer doesn't need CAMERA permission at runtime (only Host)
- Both need INTERNET

### Step 5.3: Error handling
- Network disconnection → show error + return to ConnectScreen
- Room not found → show error on ConnectScreen
- Token expired → show error on ConnectScreen

### Step 5.4: Build verification
```bash
./gradlew clean assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

---

## Files Summary

### Files to DELETE (17 files)
```
presentation/screen/sessions/SessionListScreen.kt
presentation/screen/sessions/SessionListViewModel.kt
presentation/screen/home/HomeScreen.kt
presentation/screen/drawing/DrawingScreen.kt
presentation/screen/drawing/DrawingRoute.kt
presentation/screen/drawing/DrawingViewModel.kt
presentation/screen/drawing/DrawingAction.kt
presentation/screen/drawing/DrawingEvent.kt
presentation/component/SaveSessionDialog.kt
domain/usecase/session/ (5 files)
domain/usecase/stroke/SaveStrokeUseCase.kt
domain/repository/SessionRepository.kt
domain/model/DrawingSession.kt
data/repository/SessionRepositoryImpl.kt
data/local/db/SessionDao.kt
data/local/entity/SessionEntity.kt
```

### Files to CREATE (14 files)
```
domain/model/RoomConnectionConfig.kt
domain/model/ViewerStroke.kt
presentation/screen/connect/ConnectScreen.kt
presentation/screen/connect/ConnectViewModel.kt
presentation/screen/host/HostScreen.kt
presentation/screen/host/HostRoute.kt
presentation/screen/host/HostViewModel.kt
presentation/screen/viewer/ViewerScreen.kt
presentation/screen/viewer/ViewerRoute.kt
presentation/screen/viewer/ViewerViewModel.kt
presentation/component/StrokeOverlay.kt
streaming/ViewerConnectionManager.kt
streaming/StrokeEventReceiver.kt
di/ViewerModule.kt (or add to existing module)
```

### Files to EDIT (9 files)
```
gradle/libs.versions.toml (add livekit-components)
app/build.gradle.kts (add dependency)
settings.gradle.kts (add jitpack repo if needed)
presentation/navigation/ArSketchNavGraph.kt (new routes)
presentation/component/ActionToolbar.kt (remove save/sessions)
presentation/state/DrawingUiState.kt (remove session fields)
streaming/HybridStreamingService.kt (participant count)
di/DataModule.kt (remove session dao)
di/RepositoryModule.kt (remove session binding)
MainActivity.kt (update nav entry point)
data/local/db/ArSketchDatabase.kt (remove session entity)
```

---

## Execution Order
1. **Phase 1** → Remove offline sessions (clean slate)
2. **Phase 2** → ConnectScreen (new entry point)
3. **Phase 3** → HostScreen (refactor existing drawing)
4. **Phase 4** → ViewerScreen (new feature)
5. **Phase 5** → Polish

Each phase should compile and run independently. Phase 3 and 4 can potentially be parallelized if navigation stubs are in place.

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| LiveKit Components library conflict with existing SDK | Build failure | Pin compatible versions, check transitive deps |
| 3D→2D stroke projection on viewer is inaccurate | Visual mismatch | Start with simple x,y projection, iterate |
| DataChannel message ordering on viewer | Rendering glitches | Use timestamp-based ordering, handle out-of-order |
| Large stroke data over DataChannel | Latency | Keep existing throttling (60 events/sec) |
| Token expiration during session | Disconnection | Show error + return to ConnectScreen |
