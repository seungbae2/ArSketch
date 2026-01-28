# Epic 8: Plus 기능

## 개요
- **목표**: Air Drawing 모드 및 향후 확장 기능 구현
- **예상 작업량**: 중간
- **의존성**: Epic 6 (AR Drawing) 완료
- **우선순위**: 선택적 (MVP 이후)

---

## 기능 목록

### 8.1 Air Drawing 모드 (구현 예정)
평면이 감지되지 않아도 공중에 그릴 수 있는 기능

### 8.2 WebRTC 연동 준비 (아키텍처만)
향후 원격 AR 드로잉을 위한 아키텍처 고려

---

## Task 8.1: Air Drawing 모드 구현

### 기능 설명

**Surface 모드** (기본):
- 터치 → Raycast → 감지된 평면의 교차점
- 평면이 없으면 그리기 불가

**Air 모드**:
- 터치 → 카메라 전방 고정 깊이(1.5m)에 점 생성
- 평면 감지 여부와 무관하게 그리기 가능

### 드로잉 모드 토글 UI

#### DrawingModeToggle.kt
**파일**: `presentation/component/DrawingModeToggle.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sb.arsketch.domain.model.DrawingMode

/**
 * 드로잉 모드 토글 버튼
 */
@Composable
fun DrawingModeToggle(
    currentMode: DrawingMode,
    onModeChange: (DrawingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(24.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeButton(
            text = "Surface",
            isSelected = currentMode == DrawingMode.SURFACE,
            onClick = { onModeChange(DrawingMode.SURFACE) }
        )

        ModeButton(
            text = "Air",
            isSelected = currentMode == DrawingMode.AIR,
            onClick = { onModeChange(DrawingMode.AIR) }
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        label = "mode_bg_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Color.White.copy(alpha = 0.7f)
        },
        label = "mode_text_color"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
```

### DrawingScreen 업데이트

```kotlin
// DrawingScreen.kt의 UI에 모드 토글 추가

// 상단 UI 영역에 추가
Row(
    modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    TrackingStatusIndicator(arState = uiState.arState)

    DrawingModeToggle(
        currentMode = uiState.drawingMode,
        onModeChange = viewModel::setDrawingMode
    )
}
```

---

## Task 8.2: Air Drawing 깊이 조절 UI (선택적)

### 깊이 슬라이더

#### DepthSlider.kt
**파일**: `presentation/component/DepthSlider.kt`

```kotlin
package com.sb.arsketch.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Air Drawing 깊이 조절 슬라이더
 */
@Composable
fun DepthSlider(
    depth: Float,
    onDepthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minDepth: Float = 0.5f,
    maxDepth: Float = 3.0f
) {
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f),
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "깊이",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%.1fm", depth),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Slider(
            value = depth,
            onValueChange = onDepthChange,
            valueRange = minDepth..maxDepth,
            steps = ((maxDepth - minDepth) / 0.5f).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )
    }
}
```

### ViewModel 업데이트

```kotlin
// DrawingUiState에 추가
data class DrawingUiState(
    // ... 기존 필드 ...
    val airDrawingDepth: Float = 1.5f  // 기본값 1.5m
)

// DrawingViewModel에 추가
fun setAirDrawingDepth(depth: Float) {
    _uiState.update { it.copy(airDrawingDepth = depth) }
    // AirDrawingProjector에 깊이 전달 로직 필요
}
```

---

## Task 8.3: WebRTC 통합 아키텍처 준비

### 개요

향후 WebRTC를 통한 원격 AR 드로잉을 위해 아키텍처를 준비합니다.
실제 구현은 이번 MVP 범위를 벗어나며, 인터페이스와 데이터 구조만 정의합니다.

### 스트로크 이벤트 정의

#### StrokeEvent.kt
**파일**: `domain/model/StrokeEvent.kt`

```kotlin
package com.sb.arsketch.domain.model

import kotlinx.serialization.Serializable

/**
 * 스트로크 이벤트
 * 원격 전송을 위한 직렬화 가능한 이벤트 클래스
 */
@Serializable
sealed class StrokeEvent {

    /**
     * 스트로크 시작 이벤트
     */
    @Serializable
    data class Started(
        val strokeId: String,
        val startPoint: Point3D,
        val color: Int,
        val thickness: Float,
        val mode: DrawingMode,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크에 점 추가 이벤트
     */
    @Serializable
    data class PointAdded(
        val strokeId: String,
        val point: Point3D,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크 종료 이벤트
     */
    @Serializable
    data class Ended(
        val strokeId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 스트로크 삭제 이벤트 (Undo)
     */
    @Serializable
    data class Deleted(
        val strokeId: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()

    /**
     * 모든 스트로크 삭제 이벤트
     */
    @Serializable
    data class AllCleared(
        val timestamp: Long = System.currentTimeMillis()
    ) : StrokeEvent()
}
```

### 원격 드로잉 인터페이스

#### RemoteDrawingService.kt
**파일**: `domain/service/RemoteDrawingService.kt`

```kotlin
package com.sb.arsketch.domain.service

import com.sb.arsketch.domain.model.StrokeEvent
import kotlinx.coroutines.flow.Flow

/**
 * 원격 드로잉 서비스 인터페이스
 * 향후 WebRTC 구현 시 사용
 */
interface RemoteDrawingService {

    /**
     * 연결 상태
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    /**
     * 연결 상태 Flow
     */
    val connectionState: Flow<ConnectionState>

    /**
     * 수신된 원격 이벤트 Flow
     */
    val remoteEvents: Flow<StrokeEvent>

    /**
     * 세션 연결
     */
    suspend fun connect(sessionCode: String)

    /**
     * 세션 연결 해제
     */
    suspend fun disconnect()

    /**
     * 이벤트 전송
     */
    suspend fun sendEvent(event: StrokeEvent)

    /**
     * 새 세션 생성 및 코드 반환
     */
    suspend fun createSession(): String
}
```

### 이벤트 기반 드로잉 확장

```kotlin
// DrawingViewModel에서 이벤트 발생 시 StrokeEvent 생성
// 향후 RemoteDrawingService와 연동

fun onTouchStart(point: Point3D) {
    val stroke = createStrokeUseCase(...)

    // 로컬 상태 업데이트
    _uiState.update { ... }

    // 이벤트 생성 (향후 원격 전송용)
    val event = StrokeEvent.Started(
        strokeId = stroke.id,
        startPoint = point,
        color = stroke.color,
        thickness = stroke.thickness,
        mode = stroke.mode
    )

    // TODO: remoteDrawingService?.sendEvent(event)
}
```

---

## 향후 WebRTC 구현 계획 (참고)

### 시그널링 서버
- WebSocket 기반 시그널링
- 세션 코드로 방 매칭
- Firebase Realtime Database 또는 직접 구현

### P2P 연결
- WebRTC DataChannel 사용
- 스트로크 이벤트 직렬화하여 전송
- 낮은 지연시간 실시간 동기화

### 동기화 전략
- 이벤트 기반 동기화
- 초기 연결 시 전체 상태 동기화
- 이후 이벤트 기반 증분 동기화

---

## 완료 조건

### Air Drawing 모드
- [ ] DrawingModeToggle 컴포넌트 구현
- [ ] DrawingScreen에 모드 토글 UI 추가
- [ ] Air 모드에서 평면 없이 드로잉 가능 확인
- [ ] 모드 전환 시 정상 동작 확인

### 깊이 조절 (선택적)
- [ ] DepthSlider 컴포넌트 구현
- [ ] Air 모드에서만 슬라이더 표시
- [ ] 깊이 변경 시 드로잉 위치 변경 확인

### WebRTC 준비 (아키텍처만)
- [ ] StrokeEvent 모델 정의
- [ ] RemoteDrawingService 인터페이스 정의

---

## 다음 단계

→ [Epic 9: 테스트 및 최적화](epic-09-testing.md)
