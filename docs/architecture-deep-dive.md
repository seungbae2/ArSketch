# Architecture Deep Dive

A detailed guide to ArSketch's core technical architecture, covering the full pipeline from AR rendering to WebRTC streaming.

## Table of Contents

- [1. AR Rendering Pipeline](#1-ar-rendering-pipeline)
  - [1.1 GLSurfaceView and OpenGL ES](#11-glsurfaceview-and-opengl-es)
  - [1.2 Camera Background Rendering (BackgroundRenderer)](#12-camera-background-rendering-backgroundrenderer)
  - [1.3 Plane Rendering (PlaneRenderer)](#13-plane-rendering-planerenderer)
  - [1.4 Stroke Rendering (StrokeRenderer)](#14-stroke-rendering-strokerenderer)
- [2. Touch to 3D Drawing](#2-touch-to-3d-drawing)
  - [2.1 Screen Coordinates to World Coordinates](#21-screen-coordinates-to-world-coordinates)
  - [2.2 Anchor-Based Local Coordinate System](#22-anchor-based-local-coordinate-system)
  - [2.3 Drawing Event Flow](#23-drawing-event-flow)
- [3. WebRTC Streaming Pipeline](#3-webrtc-streaming-pipeline)
  - [3.1 ARCore + LiveKit Camera Conflict](#31-arcore--livekit-camera-conflict)
  - [3.2 Frame Capture via PixelCopy](#32-frame-capture-via-pixelcopy)
  - [3.3 Double Buffering](#33-double-buffering)
  - [3.4 LiveKit VideoTrack Publishing](#34-livekit-videotrack-publishing)
  - [3.5 Service Layer (HybridStreamingService)](#35-service-layer-hybridstreamingservice)
  - [3.6 Landscape Mode Handling](#36-landscape-mode-handling)
  - [3.7 Session Management (HostStreamingSessionImpl)](#37-session-management-hoststreamingsessionimpl)
- [4. End-to-End Data Flow Summary](#4-end-to-end-data-flow-summary)

---

## 1. AR Rendering Pipeline

### 1.1 GLSurfaceView and OpenGL ES

The AR rendering uses `GLSurfaceView` instead of a plain `SurfaceView` because ARCore delivers camera frames as **OpenGL textures**.

| | SurfaceView | GLSurfaceView |
|---|---|---|
| Surface | Yes | Yes |
| OpenGL Context | No | Auto-created (EGL) |
| Render Thread | Manual | Auto-managed (GL Thread) |
| Renderer Callbacks | No | `onSurfaceCreated`, `onDrawFrame`, etc. |

`ARGLSurfaceView` extends `GLSurfaceView` with an OpenGL ES 3.0 context:

```kotlin
// ARGLSurfaceView initialization
setEGLContextClientVersion(3)
arRenderer = ARRenderer(context, arSessionManager, anchorManager)
setRenderer(arRenderer)
renderMode = RENDERMODE_CONTINUOUSLY  // Render every frame
```

GLSurfaceView automatically handles EGL context creation, GL thread management, and buffer swapping. Achieving the same with a plain SurfaceView would require implementing all of this manually.

> **Note:** GLSurfaceView is a subclass of SurfaceView, so it can be passed directly to the PixelCopy API which accepts a SurfaceView parameter.

### 1.2 Camera Background Rendering (BackgroundRenderer)

ARCore provides the camera feed as a `GL_TEXTURE_EXTERNAL_OES` (External Texture) — a special texture type sourced directly from Android's `SurfaceTexture`.

**Initialization:**

```
glGenTextures() → create textureId
session.setCameraTextureName(textureId) → register texture with ARCore
```

After calling `session.update()`, ARCore automatically writes the latest camera image to this texture.

**Rendering — Fullscreen Quad:**

A screen-filling quad is textured with the camera image to draw the background:

```
(-1,-1)──────(+1,-1)
  │              │      ← Fullscreen in NDC coordinates
  │  Camera Feed │
  │              │
(-1,+1)──────(+1,+1)
```

The shaders are straightforward:

```glsl
// Vertex Shader
gl_Position = vec4(a_Position, 0.0, 1.0);  // z=0 (background)
v_TexCoord = a_TexCoord;

// Fragment Shader
uniform samplerExternalOES u_Texture;  // Camera texture (External OES)
fragColor = texture(u_Texture, v_TexCoord);
```

**Texture Coordinate Correction:**

The camera sensor's physical orientation may differ from the device's screen orientation. ARCore's `frame.transformCoordinates2d()` handles this correction automatically:

```kotlin
frame.transformCoordinates2d(
    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,  // Input: quad vertices
    quadPositionBuffer,
    Coordinates2d.TEXTURE_NORMALIZED,                     // Output: corrected tex coords
    quadTexCoordBuffer
)
```

### 1.3 Plane Rendering (PlaneRenderer)

Renders ARCore-detected horizontal/vertical planes as semi-transparent overlays. This lets users visually confirm where they can draw in Surface mode. Can be toggled on/off.

### 1.4 Stroke Rendering (StrokeRenderer)

Strokes are rendered by connecting 3D points (`Point3D`) as `GL_LINE_STRIP`.

**Data flow:**

```
Point3D list → LineStripMesh → GPU VBO → GL_LINE_STRIP rendering
```

**LineStripMesh** — Converts point data into GPU buffers:

```kotlin
// CPU side: Point3D → FloatArray
points.forEachIndexed { index, point ->
    vertexData[offset]     = point.x
    vertexData[offset + 1] = point.y
    vertexData[offset + 2] = point.z
}

// GPU upload: transfer data to VBO
glBufferData(GL_ARRAY_BUFFER, ..., vertexBuffer, GL_DYNAMIC_DRAW)

// Render: draw connected line segments
glDrawArrays(GL_LINE_STRIP, 0, vertexCount)
```

**MVP Matrix Application:**

Each stroke is attached to an Anchor, so the Anchor's Model Matrix is used:

```
MVP = Projection × View × Model(Anchor)

Model Matrix  : Anchor's world position/orientation → transforms local coords to world
View Matrix   : camera.getViewMatrix() → transforms world coords to camera space
Projection    : camera.getProjectionMatrix(near=0.1, far=100) → projects 3D to 2D
```

```glsl
// Vertex Shader
gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);
//             ↑ MVP matrix               ↑ point in Anchor local coords
```

**Color and Thickness:**

```kotlin
// ARGB Int → RGBA float conversion
val color = floatArrayOf(
    ((stroke.color shr 16) and 0xFF) / 255f,  // R
    ((stroke.color shr 8) and 0xFF) / 255f,   // G
    (stroke.color and 0xFF) / 255f,            // B
    ((stroke.color shr 24) and 0xFF) / 255f    // A
)
glLineWidth(stroke.thickness * 500f)  // AR meters → screen pixels
```

**Per-Frame Rendering Order (ARRenderer.onDrawFrame):**

```
① session.update() → acquire Frame (camera image + tracking data)
② BackgroundRenderer.draw(frame) → camera background
③ PlaneRenderer.draw(planes, view, projection) → detected planes
④ StrokeRenderer.draw(strokes, currentStroke, view, projection) → 3D strokes
⑤ onFrameUpdate(frame) → pass current Frame to DrawingController
```

---

## 2. Touch to 3D Drawing

### 2.1 Screen Coordinates to World Coordinates

When the user touches the screen, 2D screen coordinates must be converted to 3D world coordinates. `TouchToWorldConverter` supports two modes:

```
SURFACE mode
  touch(x,y) → ARCore HitTest → intersection with detected plane → Point3D
  Can only draw on detected planes

AIR mode
  touch(x,y) → normalize → camera ray projection → point at fixed distance → Point3D
  Can draw freely in mid-air
```

### 2.2 Anchor-Based Local Coordinate System

All stroke points are stored as **relative coordinates (local coordinates)** with respect to an Anchor.

```
World Coordinate System          Anchor Local Coordinate System

     Y                             Y (up)
     │                             │
     │    Anchor ── → X            │    First touch = origin (0,0,0)
     │   ╱                         │   ╱
     │  ╱                          │  ╱
     │ ╱                           │ ╱
     ──────── X                    ──────── X
    ╱                             ╱
   Z                             Z
```

**Why local coordinates:**

ARCore continuously updates its environment map through SLAM (Simultaneous Localization and Mapping). During this process, an Anchor's world coordinates may be slightly adjusted. By storing stroke points in Anchor-relative local coordinates, strokes naturally move along with the Anchor when corrections occur.

**Coordinate Transformation:**

```kotlin
// World coordinates → Anchor local coordinates
fun worldToLocal(worldPoint: Point3D, anchorPose: Pose): Point3D {
    val anchorMatrix = FloatArray(16)
    anchorPose.toMatrix(anchorMatrix, 0)

    val inverseMatrix = FloatArray(16)
    Matrix.invertM(inverseMatrix, 0, anchorMatrix, 0)  // Anchor inverse matrix

    val worldVec = floatArrayOf(worldPoint.x, worldPoint.y, worldPoint.z, 1f)
    val localVec = FloatArray(4)
    Matrix.multiplyMV(localVec, 0, inverseMatrix, 0, worldVec, 0)

    return Point3D(localVec[0], localVec[1], localVec[2])
}
```

### 2.3 Drawing Event Flow

```
ARGLSurfaceView.onTouchEvent()
  │
  ├── ACTION_DOWN → DrawingController.onTouchDown(x, y)
  │     ├── TouchToWorldConverter.convertWithDetails()
  │     │     ├── SURFACE → HitTestHelper → ARCore hitTest() → plane intersection
  │     │     └── AIR → AirDrawingProjector → projected point in front of camera
  │     ├── AnchorManager.createAnchor() → create Anchor at first touch position
  │     ├── First point = Point3D.ZERO (Anchor local origin)
  │     └── Callback: onStrokeStartWithAnchor(StrokeStartInfo)
  │
  ├── ACTION_MOVE → DrawingController.onTouchMove(x, y)
  │     ├── TouchToWorldConverter → worldPoint
  │     ├── worldToLocal(worldPoint, anchorPose) → localPoint
  │     └── Callback: onStrokePoint(localPoint)
  │
  └── ACTION_UP → DrawingController.onTouchUp()
        └── Callback: onStrokeEnd()
```

---

## 3. WebRTC Streaming Pipeline

### 3.1 ARCore + LiveKit Camera Conflict

ARCore **exclusively owns the camera** via `session.setCameraTextureName(textureId)`. Calling LiveKit's `setCameraEnabled(true)` attempts to open a separate camera session, but since ARCore already holds the camera, only blank/black frames are produced.

**Solution:** Instead of streaming the camera directly, capture the **final OpenGL-rendered output** (camera background + strokes composited) using the **PixelCopy API**. This approach has the added benefit that viewers see the complete AR drawing in the video without needing separate stroke rendering.

### 3.2 Frame Capture via PixelCopy

PixelCopy is an Android API (24+) that **copies rendered content from a SurfaceView/GLSurfaceView into a Bitmap**.

```
┌─────────────────────────────────────────┐
│              GPU                         │
│  ┌───────────────────────────────┐      │
│  │ OpenGL Rendering Pipeline     │      │
│  │ (camera + planes + strokes)   │      │
│  └───────────┬───────────────────┘      │
│              ▼                           │
│  ┌───────────────────────────────┐      │
│  │ Surface (framebuffer)         │      │
│  └───────────┬───────────────────┘      │
│              │                           │
│      PixelCopy.request()                │
│       (GPU → CPU memory transfer)       │
│              │                           │
└──────────────┼──────────────────────────┘
               ▼
        ┌─────────────┐
        │ Bitmap (CPU) │
        └─────────────┘
```

**Why PixelCopy:**

| Method | Trade-offs |
|--------|------------|
| **PixelCopy** | Can capture SurfaceView, asynchronous, API 24+ |
| `View.draw(canvas)` | **Cannot** capture SurfaceView (renders blank) |
| `glReadPixels()` | GL thread only, synchronous blocking, complex EGL context sharing |
| `ImageReader + VirtualDisplay` | Requires MediaProjection permission, high overhead |

### 3.3 Double Buffering

PixelCopy operates asynchronously. Writing to a Bitmap that's still being copied would corrupt the data, so **double buffering** prevents race conditions:

```
Frame N  : PixelCopy → bufferA → pushBitmap(bufferA)
Frame N+1: PixelCopy → bufferB → pushBitmap(bufferB)  ← bufferA is safe
Frame N+2: PixelCopy → bufferA → pushBitmap(bufferA)  ← bufferB is safe
```

Additionally, an `AtomicBoolean(copyInProgress)` skips capture requests while a previous PixelCopy is still in progress:

```kotlin
if (!copyInProgress.compareAndSet(false, true)) return  // Skip if busy
```

**Resolution Downscaling:**

Output resolution is downscaled so the longest dimension is at most 1080px, reducing GPU readback and WebRTC encoding overhead:

```kotlin
val scale = (MAX_DIMENSION.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1f)
outputWidth = (srcWidth * scale).toInt()
outputHeight = (srcHeight * scale).toInt()
```

### 3.4 LiveKit VideoTrack Publishing

Captured Bitmaps are fed to WebRTC via LiveKit's `BitmapFrameCapturer`.

```kotlin
bitmapCapturer = BitmapFrameCapturer()

videoTrack = room.localParticipant.createVideoTrack(
    name = "arcore-stream",
    capturer = bitmapCapturer,
    options = LocalVideoTrackOptions(
        isScreencast = true,
        captureParams = VideoCaptureParameter(
            width = outputWidth,
            height = outputHeight,
            maxFps = 15,
            adaptOutputToDimensions = false
        )
    )
)

videoTrack.startCapture()  // Required!
room.localParticipant.publishVideoTrack(videoTrack)
```

**Key settings:**

| Setting | Value | Reason |
|---------|-------|--------|
| `isScreencast` | `true` | Screencast mode — prioritizes resolution over framerate |
| `maxFps` | `15` | Balances PixelCopy cost, mobile thermal, and bandwidth |
| `adaptOutputToDimensions` | `false` | Disables WebRTC automatic resolution adaptation |

**`startCapture()` is mandatory:**

`createVideoTrack()` only calls `capturer.initialize()`, and `publishVideoTrack()` does not call `startCapture()` either. Without `startCapture()`, `BitmapFrameCapturer`'s frame listener is never activated — `pushBitmap()` draws to the surface but frames never reach WebRTC.

### 3.5 Service Layer (HybridStreamingService)

Runs as an Android **Foreground Service** (`FOREGROUND_SERVICE_TYPE_CAMERA`), maintaining camera access and streaming even when the app goes to the background.

**Connection flow:**

```
connect(url, token)
  ├── LiveKit.create(appContext) → create Room
  ├── room.connect(url, token) → connect to SFU server
  ├── observeRoomEvents() → track participant count, receive DataChannel events
  └── tryStartCapture() → start ARFrameCapturer when both Room and SurfaceView are ready
```

**tryStartCapture() — Deferred initialization pattern:**

Room connection and SurfaceView setup can happen in any order, so capture only starts when both are ready:

```kotlin
private fun tryStartCapture() {
    val currentRoom = room ?: return         // Wait for Room
    val view = pendingSurfaceView ?: return  // Wait for SurfaceView
    if (state !is Connected) return          // Wait for connection

    arFrameCapturer.start(currentRoom, view) // All ready → start
}
```

**StrokeEvent DataChannel Transmission:**

Separate from video, stroke event data is also sent as JSON over DataChannel:

```kotlin
fun publishStrokeEvent(event: StrokeEvent) {
    val jsonString = json.encodeToString(event)
    room.localParticipant.publishData(
        data = jsonString.toByteArray(Charsets.UTF_8),
        reliability = DataPublishReliability.RELIABLE,
        topic = "ar_drawing"
    )
}
```

### 3.6 Landscape Mode Handling

Two issues arise during screen rotation.

**Issue 1: Streaming stops**

When the SurfaceView resizes, the existing Bitmap buffers no longer match the new dimensions. `HostRoute` detects size changes via `OnLayoutChangeListener` and restarts ARFrameCapturer:

```kotlin
// HostRoute.kt
var lastWidth = 0
var lastHeight = 0
view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
    val w = v.width
    val h = v.height
    if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
        lastWidth = w
        lastHeight = h
        drawingController.setViewportSize(w, h)
        viewModel.setGLSurfaceView(view)  // → tryStartCapture() → stop() + start()
    }
}
```

`HybridStreamingService.tryStartCapture()` always calls `stop()` before `start()` to recreate buffers matching the new dimensions.

**Issue 2: Camera orientation mismatch**

ARCore's `setDisplayGeometry(rotation, width, height)` must receive the correct rotation value. Hardcoding `0` only works in portrait mode.

```kotlin
// ARRenderer.kt — pass actual display rotation every frame
override fun onDrawFrame(gl: GL10?) {
    arSessionManager.setDisplayGeometry(getDisplayRotation(), viewportWidth, viewportHeight)
    val frame = arSessionManager.update() ?: return
    renderScene(frame)
}

private fun getDisplayRotation(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}
```

This allows ARCore to compute the difference between the camera sensor orientation and the current screen orientation, producing correct texture coordinates in `frame.transformCoordinates2d()`.

### 3.7 Session Management (HostStreamingSessionImpl)

Acts as a **bridge** between the ViewModel and the Foreground Service. Wraps Android Service's asynchronous binding into a suspend function using `suspendCancellableCoroutine`:

```
ViewModel.connect(url, token)
  └── HostStreamingSessionImpl.connect()
        ├── startForegroundService(intent)
        ├── bindService(intent, serviceConnection)
        └── suspendCancellableCoroutine { continuation ->
              // In ServiceConnection.onServiceConnected():
              //   service.setARSurfaceView(pendingSurfaceView)
              //   service.connect(url, token)
              //   continuation.resume(Unit)
              //
              // On cancellation (invokeOnCancellation):
              //   service.disconnect()
              //   unbindService()
            }
```

---

## 4. End-to-End Data Flow Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                        Host (Android)                           │
│                                                                 │
│  ┌──────────────┐    OpenGL ES 3.0    ┌──────────────────────┐  │
│  │ ARCore       │ ──────────────────→ │ ARGLSurfaceView      │  │
│  │ (camera +    │    rendering        │ (GLSurfaceView)      │  │
│  │  tracking)   │                     └──────────┬───────────┘  │
│  └──────────────┘                                │              │
│                                      PixelCopy API (async)      │
│                                                  │              │
│                                                  ▼              │
│                                       ┌──────────────────────┐  │
│                                       │ ARFrameCapturer      │  │
│                                       │ (double buffer +     │  │
│                                       │  downscale)          │  │
│                                       └──────────┬───────────┘  │
│                                                  │              │
│                                          pushBitmap()           │
│                                                  │              │
│                                                  ▼              │
│                                       ┌──────────────────────┐  │
│                                       │ BitmapFrameCapturer  │  │
│                                       │ (LiveKit SDK)        │  │
│                                       └──────────┬───────────┘  │
│                                                  │              │
│                                       WebRTC VideoTrack         │
│                                                  │              │
│  ┌──────────────┐    JSON/DataChannel ┌──────────┴───────────┐  │
│  │ StrokeEvent  │ ──────────────────→ │ LiveKit Room         │  │
│  └──────────────┘                     └──────────┬───────────┘  │
│                                                  │              │
└──────────────────────────────────────────────────┼──────────────┘
                                                   │
                                         LiveKit SFU Server
                                                   │
                                                   ▼
                                          ┌──────────────┐
                                          │  Web Viewer   │
                                          │  <video> tag  │
                                          └──────────────┘
```

**Journey of a single frame:**

1. ARCore captures a camera frame + performs 3D environment tracking
2. `ARRenderer.onDrawFrame()` — OpenGL composites camera background + planes + strokes
3. GLSurfaceView outputs the OpenGL result to its Surface
4. `ARFrameCapturer` — at 15fps timer, calls `PixelCopy.request(surfaceView, bitmap)`
5. On capture callback → `maybeScale()` → downscale to 1080p or below
6. `BitmapFrameCapturer.pushBitmap(bitmap)` → LiveKit WebRTC encoder
7. WebRTC encodes as VP8/H.264 → sends to LiveKit SFU server
8. SFU relays media to all connected Viewers
9. Web Viewer plays the stream in a `<video>` element
