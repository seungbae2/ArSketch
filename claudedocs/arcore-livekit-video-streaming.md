# ARCore + LiveKit 비디오 스트리밍 해결 과정

> ARCore가 카메라를 독점하는 환경에서 AR 렌더링 결과를 LiveKit으로 실시간 스트리밍하기까지의 전체 디버깅 및 해결 과정을 정리한 문서입니다.

---

## 배경: 왜 충돌이 발생하는가

ARCore는 카메라를 독점적으로 사용한다. `ARSession`이 시작되면 내부적으로 카메라를 열고, `session.setCameraTextureName(textureId)`를 통해 카메라 프레임을 OpenGL 텍스처로 직접 바인딩한다. 이 상태에서 다른 컴포넌트가 카메라에 접근하는 것은 불가능하다.

LiveKit의 일반적인 비디오 퍼블리시 방법인 `setCameraEnabled(true)`는 내부적으로 Android CameraX/Camera2를 통해 **별도의 카메라 세션**을 열려고 시도한다. 하지만 ARCore가 이미 카메라를 점유하고 있으므로:

- 카메라를 열지 못하거나
- 열더라도 ARCore가 렌더링하는 AR 장면(평면 감지, 3D 스트로크 등)이 아닌 **raw 카메라 피드**만 전송되거나
- 검은 화면/빈 프레임이 전송됨

**핵심 모순**: 스트리밍하고 싶은 것은 "ARCore가 렌더링한 최종 화면"이지, raw 카메라 영상이 아니다.

---

## 1단계: 문제 인식 — LiveKit 기본 방식으로는 불가

처음에는 LiveKit의 표준 API를 시도했다:

```kotlin
room.localParticipant.setCameraEnabled(true)
```

**결과**: ARCore 세션과 동시에 실행하면 카메라 접근 충돌. 웹 뷰어에서 검은 화면 또는 프레임 없음.

**분석**: LiveKit의 `setCameraEnabled(true)`는 내부적으로 `CameraCapturer`를 사용하여 독립적인 카메라 세션을 연다. ARCore와 카메라를 공유하는 메커니즘이 없다. 근본적으로 다른 접근이 필요했다.

---

## 2단계: 접근 전환 — GLSurfaceView에서 렌더링 결과를 캡처

카메라를 직접 열 수 없다면, ARCore가 렌더링한 **최종 화면**을 캡처하는 방식으로 전환했다.

Android에서 GLSurfaceView의 렌더링 결과를 비트맵으로 추출하는 방법:

| 방법 | 장단점 |
|------|--------|
| `glReadPixels()` | GL 스레드에서만 가능, 느림, 상하 반전 필요 |
| `PixelCopy API` | API 24+, 비동기, SurfaceView에서 직접 캡처, 올바른 방향 |

**선택**: `PixelCopy.request(SurfaceView, Bitmap, callback, handler)` — minSdk 24와 일치하고, 비동기로 메인 스레드를 차단하지 않는다.

---

## 3단계: LiveKit에 커스텀 비트맵을 전달하는 방법 조사

LiveKit Android SDK (2.23.3)의 소스를 Gradle 캐시에서 추출하여 분석했다:

```bash
jar tf ~/.gradle/caches/.../livekit-android-2.23.3-sources.jar | grep -i capturer
```

발견한 핵심 클래스들:

- **`BitmapFrameCapturer`** — 외부에서 `pushBitmap(bitmap, rotation)` 호출로 프레임을 주입할 수 있는 capturer
- **`VideoFrameCapturer`** — VideoFrame 객체를 직접 푸시
- **`LocalParticipant.createVideoTrack()`** — 커스텀 capturer로 VideoTrack 생성
- **`LocalParticipant.publishVideoTrack()`** — 생성된 트랙을 Room에 퍼블리시

`BitmapFrameCapturer`가 정확히 우리 용도에 맞았다.

---

## 4단계: 첫 번째 구현 — ARFrameCapturer

```kotlin
class ARFrameCapturer {
    private var bitmapCapturer: BitmapFrameCapturer? = null
    private var videoTrack: LocalVideoTrack? = null

    suspend fun start(room: Room, surfaceView: GLSurfaceView) {
        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, ARGB_8888)

        bitmapCapturer = BitmapFrameCapturer()

        videoTrack = room.localParticipant.createVideoTrack(
            name = "arcore-stream",
            capturer = bitmapCapturer!!
        )
        room.localParticipant.publishVideoTrack(videoTrack!!)

        // 캡처 루프
        handler.post(object : Runnable {
            override fun run() {
                PixelCopy.request(surfaceView, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        bitmapCapturer?.pushBitmap(bitmap, 0)
                    }
                }, handler)
                handler.postDelayed(this, 1000L / 24)
            }
        })
    }
}
```

**결과**: Android 로그에 `"Video track published: arcore-stream"` 출력. 하지만 웹 뷰어에서 `TrackSubscribed` 이벤트가 발생하지 않음.

LiveKit CLI로 확인:

```bash
lk room list --url wss://ardrawing-xabqpgun.livekit.cloud --api-key ... --api-secret ...
```

**결과: 0 publishers** — 트랙이 퍼블리시됐다고 로그에 나왔지만 실제로 SFU에 프레임이 도달하지 않았다.

---

## 5단계: 근본 원인 분석 — startCapture() 누락 발견

LiveKit SDK 소스를 깊이 파고들었다.

### BitmapFrameCapturer.kt 소스 분석

```kotlin
class BitmapFrameCapturer : VideoCapturer {
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null

    override fun initialize(
        helper: SurfaceTextureHelper,
        context: Context,
        observer: CapturerObserver
    ) {
        surfaceTextureHelper = helper
        capturerObserver = observer
        // 여기서는 리스너를 시작하지 않음!
    }

    override fun startCapture(width: Int, height: Int, fps: Int) {
        // 이것이 호출되어야 프레임 포워딩이 시작됨
        surfaceTextureHelper?.startListening { frame ->
            capturerObserver?.onFrameCaptured(frame)
        }
    }

    fun pushBitmap(bitmap: Bitmap, rotation: Int) {
        // Surface에 비트맵을 그림
        // BUT: startListening이 안 되어 있으면 → Surface에 그려지지만
        // capturerObserver.onFrameCaptured()가 호출되지 않음
        // → WebRTC에 프레임이 전달되지 않음
    }
}
```

### LocalParticipant.createVideoTrack() 소스 분석

```kotlin
fun createVideoTrack(..., capturer: VideoCapturer): LocalVideoTrack {
    val track = LocalVideoTrack(capturer, ...)
    track.initialize()  // → capturer.initialize() 호출
    // startCapture()는 호출하지 않음!
    return track
}
```

### LocalParticipant.publishVideoTrack() 소스 분석

```kotlin
suspend fun publishVideoTrack(track: LocalVideoTrack) {
    // SDP negotiation, transceiver 설정 등
    // startCapture()는 호출하지 않음!
}
```

### SDK 내부 카메라 플로우와의 차이

SDK 내부에서 기본 카메라를 사용할 때:

```
setCameraEnabled(true)
  → createVideoTrack() → initialize()
  → track.startCapture()      ← SDK가 내부적으로 호출
  → publishVideoTrack()
```

커스텀 capturer를 사용할 때는 `startCapture()`를 **개발자가 직접 호출해야** 한다. 하지만 이 사실이 SDK 문서에 명시되어 있지 않았다.

### 프레임 전달 흐름 비교

```
startCapture() 미호출 시:
  pushBitmap() → Surface에 그림 → 리스너 없음 → capturerObserver 호출 안 됨 → WebRTC 무반응

startCapture() 호출 시:
  pushBitmap() → Surface에 그림 → startListening 리스너 → onFrameCaptured() → WebRTC → SFU
```

---

## 6단계: 수정 — startCapture() 추가

```kotlin
videoTrack = room.localParticipant.createVideoTrack(
    name = "arcore-stream",
    capturer = bitmapCapturer!!,
    options = LocalVideoTrackOptions(
        isScreencast = true,
        captureParams = VideoCaptureParameter(
            width = outputWidth,
            height = outputHeight,
            maxFps = fps,
            adaptOutputToDimensions = false  // 비 16:9 해상도 왜곡 방지
        )
    )
)

videoTrack!!.startCapture()  // 핵심: 이 한 줄이 없으면 프레임이 WebRTC에 도달하지 않음

room.localParticipant.publishVideoTrack(videoTrack!!)
```

**결과**: `lk room list` → **1 publisher**. 웹 뷰어에서 `TrackSubscribed` 이벤트 발생. AR 영상이 실시간으로 표시됨.

---

## 7단계: 성능 최적화

영상은 전송되었지만 화질이 낮고 심하게 끊겼다.

### 문제 1: 해상도 과다

- 원본: 1440x3120 (삼성 기기) = 프레임당 ~18MB
- 기본 비트레이트 ~1.7Mbps로는 4.5M 픽셀을 감당 불가

### 문제 2: 레이스 컨디션

- 단일 비트맵을 재사용 → PixelCopy(비동기)가 완료되기 전에 다음 캡처가 같은 비트맵에 쓰기 시작

### 문제 3: 프레임 적체

- 24fps로 PixelCopy 요청 → 이전 요청 완료 전 다음 요청 큐잉 → 지연 누적

### 해결

```kotlin
companion object {
    private const val DEFAULT_FPS = 15
    private const val MAX_DIMENSION = 1080
}

// 1. 해상도 축소: 긴 변 1080px 이하로
val scale = (MAX_DIMENSION.toFloat() / maxOf(srcWidth, srcHeight)).coerceAtMost(1f)
outputWidth = (srcWidth * scale).toInt()
outputHeight = (srcHeight * scale).toInt()

// 2. 더블 버퍼링: 두 개의 비트맵을 교대 사용
bufferA = Bitmap.createBitmap(srcWidth, srcHeight, ARGB_8888)
bufferB = Bitmap.createBitmap(srcWidth, srcHeight, ARGB_8888)

private fun captureFrame(surfaceView: GLSurfaceView) {
    // 3. 프레임 스킵: 이전 캡처 진행 중이면 건너뜀
    if (!copyInProgress.compareAndSet(false, true)) return

    val captureBuffer = if (currentCaptureBuffer == 0) bufferA else bufferB
    currentCaptureBuffer = 1 - currentCaptureBuffer  // 버퍼 교체

    PixelCopy.request(surfaceView, captureBuffer!!, { result ->
        if (result == PixelCopy.SUCCESS) {
            bitmapCapturer?.pushBitmap(maybeScale(captureBuffer), 0)
        }
        copyInProgress.set(false)  // 완료 후 다음 캡처 허용
    }, pixelCopyHandler!!)
}
```

---

## 8단계: 가로모드 대응

### 문제 A: 회전 시 스트리밍 멈춤

`ARFrameCapturer.start()`에 `if (isCapturing) return` 가드가 있어 재시작 불가.

**해결**: `HybridStreamingService.tryStartCapture()`에서 `stop()` 후 `start()` 호출. `OnLayoutChangeListener`로 GLSurfaceView 크기 변경 감지.

```kotlin
// HybridStreamingService.kt
private fun tryStartCapture() {
    val currentRoom = room ?: return
    val view = pendingSurfaceView ?: return
    if (_streamingState.value !is StreamingState.Streaming) return
    pendingSurfaceView = null

    serviceScope.launch {
        arFrameCapturer.stop()  // 기존 캡처 중지 (회전 대응)
        arFrameCapturer.start(currentRoom, view)
    }
}
```

```kotlin
// HostRoute.kt — GLSurfaceView에 레이아웃 변경 리스너 추가
var lastWidth = 0
var lastHeight = 0
view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
    val w = v.width
    val h = v.height
    if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
        lastWidth = w
        lastHeight = h
        drawingController.setViewportSize(w, h)
        viewModel.setGLSurfaceView(view)  // → tryStartCapture() 트리거
    }
}
```

### 문제 B: 가로인데 영상이 세로 방향

`ARRenderer.setDisplayGeometry(0, w, h)` — rotation이 항상 0으로 고정되어 있었다.

**해결**: 매 프레임마다 `getDisplayRotation()` (실제 `Surface.ROTATION_*` 값)을 ARCore에 전달.

```kotlin
// ARRenderer.kt
override fun onDrawFrame(gl: GL10?) {
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    if (!isTextureSet) return

    // 매 프레임마다 실제 display rotation을 ARCore에 전달
    arSessionManager.setDisplayGeometry(getDisplayRotation(), viewportWidth, viewportHeight)

    val frame = arSessionManager.update() ?: return
    renderScene(frame)
    onFrameUpdate?.invoke(frame)
}

@Suppress("DEPRECATION")
private fun getDisplayRotation(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }
}
```

---

## 최종 아키텍처

```
┌─ Android Host ──────────────────────────────────────────────┐
│                                                              │
│  ARCore Session                                              │
│    ├─ Camera (독점)                                           │
│    ├─ Plane Detection                                        │
│    └─ Tracking                                               │
│          ↓                                                   │
│  ARRenderer (GLSurfaceView.Renderer)                         │
│    ├─ BackgroundRenderer (카메라 텍스처)                        │
│    ├─ PlaneRenderer (감지된 평면)                              │
│    ├─ StrokeRenderer (3D 스트로크)                             │
│    └─ setDisplayGeometry(getDisplayRotation(), w, h)         │
│          ↓ (최종 렌더링된 프레임)                                │
│  GLSurfaceView                                               │
│          ↓                                                   │
│  ARFrameCapturer                                             │
│    ├─ PixelCopy.request(surfaceView, buffer)                 │
│    ├─ 더블 버퍼링 (bufferA ⇄ bufferB)                         │
│    ├─ AtomicBoolean 프레임 스킵                                │
│    ├─ 해상도 축소 (MAX 1080px)                                 │
│    └─ BitmapFrameCapturer.pushBitmap()                       │
│          ↓                                                   │
│  LiveKit VideoTrack                                          │
│    ├─ createVideoTrack(capturer, adaptOutput=false)          │
│    ├─ startCapture() ← 핵심!                                  │
│    └─ publishVideoTrack()                                    │
│          ↓                                                   │
│  LiveKit SFU (WebRTC)                                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
          ↓
┌─ Web Viewer ─────────────────────────────────────────────────┐
│  LiveKit Room.connect()                                      │
│    → TrackSubscribed → <video> element                       │
└──────────────────────────────────────────────────────────────┘
```

---

## 핵심 교훈

1. **`BitmapFrameCapturer.startCapture()`는 반드시 수동 호출해야 한다** — SDK 문서에 명시되지 않았지만, 이것 없이는 `pushBitmap()`이 Surface에 그리기만 하고 WebRTC로 프레임이 포워딩되지 않는다.

2. **ARCore는 카메라를 독점한다** — 다른 라이브러리와 카메라를 공유하는 공식 방법이 없으며, 렌더링 결과를 캡처하는 우회 전략이 필요하다.

3. **PixelCopy는 비동기** — 단일 비트맵 재사용 시 레이스 컨디션 발생, 더블 버퍼링 필수.

4. **`adaptOutputToDimensions = false`** — 기본값 `true`는 16:9가 아닌 해상도에서 `ScaleCropVideoProcessor`를 적용하여 영상을 왜곡시킨다.

5. **ARCore의 `setDisplayGeometry()`에 실제 rotation 전달 필수** — 하드코딩 `0`은 세로모드 전용. 가로모드에서 카메라 방향이 틀어진다.

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `app/.../streaming/ARFrameCapturer.kt` | PixelCopy + BitmapFrameCapturer로 AR 프레임 캡처 및 스트리밍 |
| `app/.../streaming/HybridStreamingService.kt` | LiveKit Room 관리, 비디오/데이터 퍼블리시, 포그라운드 서비스 |
| `app/.../ar/renderer/ARRenderer.kt` | ARCore 렌더링, display rotation 처리 |
| `app/.../ar/core/ARGLSurfaceView.kt` | GLSurfaceView 래퍼, 터치 이벤트 |
| `app/.../presentation/screen/host/HostRoute.kt` | Compose Route, OnLayoutChangeListener로 회전 감지 |
| `web-viewer/src/main.ts` | 웹 뷰어 진입점, LiveKit 연결 및 비디오 표시 |

## 환경 정보

- LiveKit Android SDK: 2.23.3
- ARCore SDK: Google AR Core
- Android minSdk: 24 (PixelCopy API 24+ 필요)
- 테스트 기기: Samsung (1440x3120 해상도)
