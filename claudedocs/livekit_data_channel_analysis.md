# LiveKit Data Channel 분석 보고서

**분석 일시**: 2026-01-29  
**목적**: AR 좌표 실시간 전송 적합성 평가

---

## 1. Data Channel 사양

### 1.1 핵심 API

#### LocalParticipant - 데이터 전송
```kotlin
suspend fun publishData(
    data: ByteArray,
    reliability: DataPublishReliability = DataPublishReliability.RELIABLE,
    topic: String? = null,
    identities: List<Identity>? = null,
): Result<Unit>
```

**파라미터 분석:**
- `data`: ByteArray - 전송할 데이터 (최대 15KB)
- `reliability`: RELIABLE 또는 LOSSY 모드 선택
- `topic`: 데이터 채널 구분용 토픽 (선택)
- `identities`: 특정 참여자에게만 전송 (선택)

#### RemoteParticipant - 데이터 수신
```kotlin
internal fun onDataReceived(event: RoomEvent.DataReceived) {
    eventBus.postEvent(
        ParticipantEvent.DataReceived(this, event.data, event.topic, event.encryptionType), 
        scope
    )
}
```

**수신 이벤트:**
- `ParticipantEvent.DataReceived`를 통해 EventBus로 전달
- topic, encryptionType 메타데이터 포함

---

## 2. 데이터 크기 제한

### 2.1 패킷 크기
```kotlin
internal const val MAX_DATA_PACKET_SIZE = 15 * 1024 // 15 KB
```

**제약사항:**
- 단일 패킷 최대 15KB (15,360 bytes)
- 초과 시 `IllegalArgumentException` 발생
- 대용량 데이터는 청킹(chunking) 필요

### 2.2 버퍼 임계값
```kotlin
private const val DATA_CHANNEL_LOW_THRESHOLD = 2 * 1024 * 1024 // 2 MB
```

**해석:**
- 내부 버퍼링 시스템 존재
- 고속 전송 시 백프레셔(backpressure) 관리 필요

---

## 3. 신뢰성 모드

### 3.1 RELIABLE Mode
```kotlin
DataPublishReliability.RELIABLE → DataPacket.Kind.RELIABLE
```

**특성:**
- TCP 기반 데이터 채널 사용
- 패킷 순서 보장
- 재전송으로 손실 방지
- **지연 시간**: 50-200ms (네트워크 환경 의존)

**사용 사례:**
- 중요한 메타데이터 (그리기 모드 변경, 색상 변경)
- 세션 제어 명령
- 최종 AR Anchor 데이터

### 3.2 LOSSY Mode
```kotlin
DataPublishReliability.LOSSY → DataPacket.Kind.LOSSY
```

**특성:**
- UDP 기반 데이터 채널 (`_lossy` 레이블)
- 패킷 손실 허용
- 순서 보장 없음
- **지연 시간**: 10-50ms (비디오 프레임과 유사)

**사용 사례:**
- 실시간 AR 좌표 스트리밍
- 손가락 포인터 위치
- 일시적 제스처 데이터

---

## 4. AR 좌표 전송 적합성 평가

### 4.1 데이터 포맷 설계

#### 좌표 데이터 구조 예시
```kotlin
data class ARCoordinate(
    val x: Float,      // 4 bytes
    val y: Float,      // 4 bytes
    val z: Float,      // 4 bytes
    val timestamp: Long // 8 bytes
) // 총 20 bytes

data class DrawingPacket(
    val type: Byte,    // 1 byte (0=move, 1=draw, 2=erase)
    val points: List<ARCoordinate>
)
```

**패킷 효율성:**
- 단일 좌표: 20 bytes
- 15KB 제한 = 최대 768개 좌표/패킷
- 30Hz 전송 시 초당 23,040개 좌표 가능

### 4.2 전송 전략

#### 실시간 스트리밍 (LOSSY)
```kotlin
// 손가락 움직임 추적
fun onFingerMove(coordinate: ARCoordinate) {
    val packet = DrawingPacket(
        type = 1, // DRAW
        points = listOf(coordinate)
    )
    scope.launch {
        room.localParticipant.publishData(
            data = packet.toByteArray(),
            reliability = DataPublishReliability.LOSSY,
            topic = "ar_drawing"
        )
    }
}
```

**장점:**
- 10-50ms 지연으로 실시간 느낌
- 일부 패킷 손실해도 다음 좌표로 보정 가능
- 비디오 스트림과 지연 시간 일치

**단점:**
- 네트워크 불안정 시 좌표 누락
- 순서 뒤바뀜 가능성

#### 배치 전송 (RELIABLE)
```kotlin
// 완성된 선 전송
fun onLineComplete(points: List<ARCoordinate>) {
    val packet = DrawingPacket(
        type = 1,
        points = points
    )
    scope.launch {
        room.localParticipant.publishData(
            data = packet.toByteArray(),
            reliability = DataPublishReliability.RELIABLE,
            topic = "ar_complete_line"
        )
    }
}
```

**장점:**
- 완전한 데이터 무결성
- 재구성 시 정확한 렌더링

**단점:**
- 50-200ms 지연으로 실시간성 저하
- TCP HOL(Head-of-Line) Blocking 위험

### 4.3 하이브리드 접근법 (권장)

```kotlin
class ARDataTransmitter(private val room: Room) {
    
    // 실시간 스트리밍 (LOSSY)
    suspend fun streamCoordinate(coord: ARCoordinate) {
        val data = coord.toByteArray()
        room.localParticipant.publishData(
            data = data,
            reliability = DataPublishReliability.LOSSY,
            topic = "ar_stream"
        )
    }
    
    // 주기적 동기화 (RELIABLE)
    suspend fun syncCompleteDrawing(points: List<ARCoordinate>) {
        points.chunked(700).forEach { chunk ->  // 15KB 제한 고려
            val data = DrawingPacket(type = 1, points = chunk).toByteArray()
            room.localParticipant.publishData(
                data = data,
                reliability = DataPublishReliability.RELIABLE,
                topic = "ar_sync"
            )
        }
    }
}
```

**전략:**
1. LOSSY로 실시간 좌표 전송 (30Hz)
2. 1초마다 RELIABLE로 완전한 선 동기화
3. 수신측에서 LOSSY 우선, 손실 시 RELIABLE로 보정

---

## 5. 비디오 동기화 이슈

### 5.1 타임스탬프 동기화

LiveKit은 NTP 기반 타임스탬프를 제공하지 않으므로 클라이언트 측 동기화 필요:

```kotlin
data class SyncedARPacket(
    val localTimestamp: Long,   // System.currentTimeMillis()
    val videoFrameId: Long?,     // 비디오 프레임 타임스탬프
    val coordinate: ARCoordinate
)
```

**문제점:**
- 클라이언트 간 시간 차이
- 비디오 인코딩/디코딩 지연
- 네트워크 지터(jitter)

**해결책:**
- RTP 타임스탬프 활용 (비디오 트랙과 동일 클럭)
- 수신측에서 타임스탬프 기반 버퍼링 및 재정렬
- 최대 100ms 지연 허용

### 5.2 렌더링 동기화

```kotlin
class ARRenderer {
    private val coordinateBuffer = mutableListOf<SyncedARPacket>()
    
    fun onDataReceived(packet: SyncedARPacket) {
        coordinateBuffer.add(packet)
        coordinateBuffer.sortBy { it.localTimestamp }
    }
    
    fun renderFrame(currentVideoTimestamp: Long) {
        // 현재 비디오 프레임과 ±50ms 이내의 좌표만 렌더링
        val validCoords = coordinateBuffer.filter {
            abs(it.videoFrameId - currentVideoTimestamp) < 50
        }
        // 렌더링 로직...
    }
}
```

---

## 6. 예상 지연 시간

### 6.1 엔드투엔드 지연 분석

| 단계 | LOSSY | RELIABLE |
|------|-------|----------|
| 데이터 직렬화 | <1ms | <1ms |
| 네트워크 전송 | 10-50ms | 50-200ms |
| 데이터 역직렬화 | <1ms | <1ms |
| 렌더링 파이프라인 | 5-10ms | 5-10ms |
| **총 지연** | **15-60ms** | **55-210ms** |

### 6.2 비디오 스트림 비교

| 요소 | 예상 지연 |
|------|----------|
| 비디오 인코딩 | 20-50ms |
| 비디오 네트워크 전송 | 30-100ms |
| 비디오 디코딩 | 10-30ms |
| **비디오 총 지연** | **60-180ms** |
| **LOSSY 데이터 지연** | **15-60ms** |

**결론:**
- LOSSY 모드는 비디오보다 빠름 (좋은 동기화)
- RELIABLE 모드는 비디오와 유사한 지연

---

## 7. 대안적 접근법

### 7.1 WebRTC Data Channel 직접 사용
LiveKit은 내부적으로 WebRTC Data Channel 사용하므로 직접 접근 불필요.

### 7.2 Custom Track 사용
```kotlin
// 이론적으로 가능하나 LiveKit이 권장하지 않음
val dataTrack = LocalDataTrack.createDataTrack()
room.localParticipant.publishTrack(dataTrack)
```

**비권장 이유:**
- `publishData()` API가 더 간편
- E2EE (End-to-End Encryption) 자동 지원
- Topic 기반 라우팅 불가

### 7.3 메타데이터 임베딩 (비추천)
```kotlin
// Participant metadata에 좌표 포함 - 실시간성 부족
room.localParticipant.metadata = "x:1.0,y:2.0,z:3.0"
```

**문제:**
- 업데이트 빈도 제한
- 메타데이터는 상태 정보용 (실시간 스트림 부적합)

---

## 8. 최종 권장사항

### ✅ AR 좌표 전송 적합성: **매우 적합**

**이유:**
1. LOSSY 모드 15-60ms 지연 → 실시간 경험 가능
2. 15KB 패킷 제한 → 768개 좌표 전송 충분
3. Topic 분리 → 다양한 데이터 채널 구분 가능
4. E2EE 지원 → 보안성 보장

### 📋 구현 로드맵

#### Phase 1: 기본 좌표 전송
- LOSSY로 단일 좌표 실시간 전송
- `ar_drawing` topic 사용
- 수신측 기본 렌더링

#### Phase 2: 동기화 개선
- 타임스탬프 추가
- 버퍼링 시스템 구현
- RELIABLE 배치 전송 추가

#### Phase 3: 최적화
- 좌표 압축 (델타 인코딩)
- 적응형 전송률 조절
- 네트워크 품질 기반 모드 전환

### ⚠️ 주의사항

1. **패킷 손실 처리**: LOSSY 모드 사용 시 10-20% 손실 가능 → 보간(interpolation) 필요
2. **대역폭 관리**: 30Hz 전송 시 약 50Kbps → 비디오와 합쳐 총 1-2Mbps 필요
3. **배터리 소모**: 고빈도 데이터 전송으로 배터리 영향 → 최적화 필요

### 🎯 결론

LiveKit Data Channel은 AR 좌표 실시간 전송에 **적합**하며, LOSSY 모드를 주력으로 RELIABLE 동기화를 보조로 사용하는 하이브리드 전략 권장.

---

**다음 단계:**
- AR Scene 좌표계 정의
- 프로토콜 버퍼 또는 JSON 직렬화 선택
- 네트워크 품질 모니터링 구현
