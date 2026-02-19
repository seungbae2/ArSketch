# ARCore + LiveKit Video Streaming Troubleshooting

> A record of the debugging and resolution process for streaming AR-rendered output to LiveKit in an environment where ARCore exclusively owns the camera.
>
> For how the current architecture works, see [Architecture Deep Dive](architecture-deep-dive.md).

---

## Step 1: Problem — LiveKit's Default Approach Doesn't Work

The first attempt used LiveKit's standard API:

```kotlin
room.localParticipant.setCameraEnabled(true)
```

**Result**: Camera access conflict when running alongside an ARCore session. Black screen or no frames in the web viewer.

**Analysis**: ARCore exclusively owns the camera via `session.setCameraTextureName(textureId)`. LiveKit's `setCameraEnabled(true)` internally uses `CameraCapturer` to open an independent camera session, which cannot share the camera with ARCore. A fundamentally different approach was needed.

---

## Step 2: Pivot — Capture the Rendered Output Instead

Since the camera can't be opened directly, the strategy shifted to capturing **ARCore's final rendered screen**.

Chose `PixelCopy.request(SurfaceView, Bitmap, callback, handler)` as the capture method — matches minSdk 24 and is asynchronous so it doesn't block the main thread.

> For why PixelCopy was chosen and alternatives comparison, see [Architecture Deep Dive § 3.2](architecture-deep-dive.md#32-frame-capture-via-pixelcopy).

---

## Step 3: Investigating How to Feed Custom Bitmaps to LiveKit

Extracted and analyzed the LiveKit Android SDK (2.23.3) source from the Gradle cache:

```bash
jar tf ~/.gradle/caches/.../livekit-android-2.23.3-sources.jar | grep -i capturer
```

Key classes discovered:

- **`BitmapFrameCapturer`** — A capturer that accepts externally pushed frames via `pushBitmap(bitmap, rotation)`
- **`VideoFrameCapturer`** — Pushes VideoFrame objects directly
- **`LocalParticipant.createVideoTrack()`** — Creates a VideoTrack with a custom capturer
- **`LocalParticipant.publishVideoTrack()`** — Publishes the created track to the Room

`BitmapFrameCapturer` was exactly what we needed.

---

## Step 4: First Implementation — Frames Not Reaching the SFU

```kotlin
bitmapCapturer = BitmapFrameCapturer()
videoTrack = room.localParticipant.createVideoTrack("arcore-stream", bitmapCapturer!!)
room.localParticipant.publishVideoTrack(videoTrack!!)

// Capture loop: PixelCopy → pushBitmap
```

**Result**: Android logcat showed `"Video track published: arcore-stream"`. But no `TrackSubscribed` event in the web viewer.

Verified with LiveKit CLI:

```bash
lk room list --url wss://ardrawing-xabqpgun.livekit.cloud --api-key ... --api-secret ...
```

**Result: 0 publishers** — The track appeared to be published in the logs, but no frames were actually reaching the SFU.

---

## Step 5: Root Cause — Missing startCapture()

Deep analysis of the SDK source revealed `BitmapFrameCapturer`'s internal structure:

```kotlin
class BitmapFrameCapturer : VideoCapturer {
    override fun initialize(helper, context, observer) {
        surfaceTextureHelper = helper
        capturerObserver = observer
        // Does NOT start the listener!
    }

    override fun startCapture(width, height, fps) {
        // THIS must be called to begin frame forwarding
        surfaceTextureHelper?.startListening { frame ->
            capturerObserver?.onFrameCaptured(frame)
        }
    }

    fun pushBitmap(bitmap, rotation) {
        // Draws bitmap to the surface
        // BUT: if startListening hasn't been called
        // → Draws to surface but onFrameCaptured() is never called
        // → Frames never reach WebRTC
    }
}
```

**Difference from SDK's internal camera flow:**

```
setCameraEnabled(true)
  → createVideoTrack() → initialize()
  → track.startCapture()      ← SDK calls this internally
  → publishVideoTrack()

Custom capturer usage:
  → createVideoTrack() → initialize()
  → (startCapture not called!)  ← Developer must call this manually
  → publishVideoTrack()
```

**Frame delivery flow comparison:**

```
Without startCapture():
  pushBitmap() → draws to surface → no listener → WebRTC receives nothing

With startCapture():
  pushBitmap() → draws to surface → startListening → onFrameCaptured() → WebRTC → SFU
```

---

## Step 6: Fix — Adding startCapture()

```kotlin
videoTrack = room.localParticipant.createVideoTrack(
    name = "arcore-stream",
    capturer = bitmapCapturer!!,
    options = LocalVideoTrackOptions(
        isScreencast = true,
        captureParams = VideoCaptureParameter(
            width = outputWidth, height = outputHeight,
            maxFps = fps,
            adaptOutputToDimensions = false
        )
    )
)

videoTrack!!.startCapture()  // The critical one-liner

room.localParticipant.publishVideoTrack(videoTrack!!)
```

**Result**: `lk room list` → **1 publisher**. AR video streaming live in the web viewer.

---

## Step 7: Performance Optimization

Video was streaming but quality was poor and stuttering was severe.

### Problem 1: Excessive Resolution

- Source: 1440x3120 (Samsung device) = ~18MB per frame
- Default bitrate ~1.7Mbps cannot handle 4.5M pixels

**Fix**: Downscale longest dimension to 1080px (`MAX_DIMENSION = 1080`)

### Problem 2: Race Condition

- Reusing a single bitmap → PixelCopy (async) hadn't finished before the next capture started writing to the same bitmap

**Fix**: Double buffering (alternating bufferA ⇄ bufferB)

### Problem 3: Frame Accumulation

- PixelCopy requests at 24fps → requests queuing before previous ones complete → latency buildup

**Fix**: `AtomicBoolean(copyInProgress)` — skip capture if previous one is still in progress, reduced FPS to 15

> For the optimized final implementation, see [Architecture Deep Dive § 3.3 Double Buffering](architecture-deep-dive.md#33-double-buffering).

---

## Step 8: Landscape Mode Handling

### Problem A: Streaming stops on rotation

`ARFrameCapturer.start()` had an `if (isCapturing) return` guard preventing restart.

**Fix**: `HybridStreamingService.tryStartCapture()` calls `stop()` then `start()`. `OnLayoutChangeListener` detects SurfaceView size changes.

```kotlin
// HostRoute.kt — layout change listener on SurfaceView
var lastWidth = 0
var lastHeight = 0
view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
    val w = v.width
    val h = v.height
    if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
        lastWidth = w
        lastHeight = h
        drawingController.setViewportSize(w, h)
        viewModel.setGLSurfaceView(view)  // → triggers tryStartCapture()
    }
}
```

### Problem B: Landscape video rendered in portrait orientation

`ARRenderer.setDisplayGeometry(0, w, h)` — rotation was hardcoded to 0.

**Fix**: Pass the actual `Surface.ROTATION_*` value to ARCore every frame.

```kotlin
override fun onDrawFrame(gl: GL10?) {
    // Pass actual display rotation to ARCore every frame
    arSessionManager.setDisplayGeometry(getDisplayRotation(), viewportWidth, viewportHeight)
    // ...
}
```

> For the current landscape mode implementation, see [Architecture Deep Dive § 3.6 Landscape Mode Handling](architecture-deep-dive.md#36-landscape-mode-handling).

---

## Key Lessons

1. **`BitmapFrameCapturer.startCapture()` must be called manually** — Not documented in the SDK, but without it `pushBitmap()` only draws to the surface without forwarding frames to WebRTC.

2. **ARCore exclusively owns the camera** — There is no official way to share the camera with other libraries. A workaround strategy of capturing the rendered output is required.

3. **PixelCopy is asynchronous** — Reusing a single bitmap causes race conditions. Double buffering is essential.

4. **`adaptOutputToDimensions = false`** — The default `true` applies `ScaleCropVideoProcessor` on non-16:9 resolutions, distorting the video.

5. **ARCore's `setDisplayGeometry()` requires the actual rotation** — Hardcoding `0` is portrait-only. Camera orientation breaks in landscape mode.

---

## Related Files

| File | Role |
|------|------|
| `core/streaming/.../ARFrameCapturer.kt` | AR frame capture and streaming via PixelCopy + BitmapFrameCapturer |
| `core/streaming/.../HybridStreamingService.kt` | LiveKit Room management, video/data publishing, foreground service |
| `core/ar/.../renderer/ARRenderer.kt` | ARCore rendering, display rotation handling |
| `core/ar/.../core/ARGLSurfaceView.kt` | GLSurfaceView wrapper, touch event handling |
| `feature/host/.../HostRoute.kt` | Compose Route, OnLayoutChangeListener for rotation detection |
| `web-viewer/src/main.ts` | Web viewer entry point, LiveKit connection and video display |

## Environment

- LiveKit Android SDK: 2.23.3
- ARCore SDK: 1.52
- Android minSdk: 24 (PixelCopy requires API 24+)
- Test device: Samsung (1440x3120 resolution)
