# AirSketch AR - 프로젝트 계획 개요

## 1. 프로젝트 소개

**AirSketch AR**은 ARCore를 활용하여 현실 공간에 3D 드로잉을 그릴 수 있는 Android AR 애플리케이션입니다.

### 핵심 목표
- ARCore Raycast / HitTest 구현 역량 증명
- 2D 입력 → 3D World Coordinate 변환 기술 시연
- 실시간 Polyline(Line Strip) 렌더링 구현
- 성능 최적화 (포인트 리샘플링, 메쉬 업데이트 쓰로틀링)

---

## 2. 기술 결정 사항

| 결정 항목 | 선택 | 이유 |
|----------|------|------|
| 데이터 저장 | Room Database | 타입 안전, 구조화된 쿼리, 확장성 |
| 네트워킹 | WebRTC 대비 설계 | 향후 원격 AR 드로잉 기능 구현 예정 |
| AR 렌더러 | Pure OpenGL ES | Line Strip 커스텀 렌더링 최대 제어 |
| Air Drawing 깊이 | 1.5미터 | 편안한 팔 길이 거리 |

---

## 3. 기술 스택

| 카테고리 | 기술 |
|---------|------|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM, Clean Architecture |
| Async | Coroutines, Flow |
| DI | Hilt |
| Database | Room |
| AR | ARCore |
| Rendering | OpenGL ES (Line Strip) |
| Navigation | Navigation Compose |
| Logging | Timber |

---

## 4. 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                        │
│  (Jetpack Compose UI, ViewModels, StateFlow, Navigation)    │
├─────────────────────────────────────────────────────────────┤
│                      Domain Layer                            │
│  (Use Cases, Domain Models, Repository Interfaces)          │
├─────────────────────────────────────────────────────────────┤
│                       Data Layer                             │
│  (Room Database, Repository Impl, Entity Mappers)           │
├─────────────────────────────────────────────────────────────┤
│                        AR Layer                              │
│  (ARCore Session, OpenGL Renderer, Geometry, Hit Testing)   │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Epic 목록

| Epic | 제목 | 설명 | 문서 |
|------|------|------|------|
| 1 | 프로젝트 설정 | 의존성 추가, 프로젝트 구조 생성 | [epic-01-project-setup.md](epics/epic-01-project-setup.md) |
| 2 | Domain Layer | 도메인 모델, 인터페이스, Use Case | [epic-02-domain-layer.md](epics/epic-02-domain-layer.md) |
| 3 | Data Layer | Room DB, Repository 구현 | [epic-03-data-layer.md](epics/epic-03-data-layer.md) |
| 4 | AR Foundation | ARCore 세션, OpenGL 렌더러 | [epic-04-ar-foundation.md](epics/epic-04-ar-foundation.md) |
| 5 | Presentation Layer | Compose UI, ViewModel | [epic-05-presentation-layer.md](epics/epic-05-presentation-layer.md) |
| 6 | AR Drawing 구현 | 터치 입력 → AR 렌더링 파이프라인 | [epic-06-ar-drawing.md](epics/epic-06-ar-drawing.md) |
| 7 | 저장/불러오기 | 세션 저장 및 복원 기능 | [epic-07-save-load.md](epics/epic-07-save-load.md) |
| 8 | Plus 기능 | Air Drawing 모드 | [epic-08-plus-features.md](epics/epic-08-plus-features.md) |
| 9 | 테스트 및 최적화 | 단위 테스트, 성능 최적화 | [epic-09-testing.md](epics/epic-09-testing.md) |

---

## 6. 패키지 구조

```
com.sb.arsketch/
├── ArSketchApplication.kt          # @HiltAndroidApp
├── MainActivity.kt                  # @AndroidEntryPoint
│
├── di/                              # Hilt DI 모듈
│   ├── DataModule.kt
│   ├── RepositoryModule.kt
│   └── ARModule.kt
│
├── data/
│   ├── local/
│   │   ├── db/                     # Room 데이터베이스
│   │   └── entity/                 # Room 엔티티
│   ├── repository/                 # Repository 구현체
│   └── mapper/                     # Entity ↔ Domain 매퍼
│
├── domain/
│   ├── model/                      # 도메인 모델
│   ├── repository/                 # Repository 인터페이스
│   └── usecase/                    # Use Case
│
├── presentation/
│   ├── navigation/                 # Nav Graph
│   ├── screen/                     # 화면 Composable
│   ├── component/                  # 재사용 UI 컴포넌트
│   ├── viewmodel/                  # ViewModel
│   └── state/                      # UI State 클래스
│
├── ar/
│   ├── core/                       # ARCore 세션 관리
│   ├── renderer/                   # OpenGL 렌더러
│   ├── geometry/                   # 메쉬, Line Strip 생성
│   └── util/                       # 좌표 변환, Hit Testing
│
└── util/                           # 공통 유틸리티
```

---

## 7. 의존성 추가 목록

```toml
# ARCore
arcore = "1.42.0"

# Room
room = "2.6.1"

# Hilt
hilt = "2.51"

# Navigation
navigationCompose = "2.7.7"

# Timber
timber = "5.0.1"

# Serialization (Point 직렬화용)
kotlinxSerialization = "1.6.3"
```

---

## 8. 검증 계획

### 빌드 검증
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### 수동 테스트 체크리스트
- [ ] AR 세션 초기화 성공
- [ ] 평면 감지 동작
- [ ] 터치로 스트로크 생성
- [ ] 색상/두께 적용 확인
- [ ] Undo/Redo 동작
- [ ] Clear All 동작
- [ ] 세션 저장/불러오기
- [ ] Air Drawing 동작
- [ ] 프레임 드랍 없이 안정적 동작

---

## 9. 향후 확장 계획

- **WebRTC 통합**: 원격 화상 통화에서 AR 드로잉 공유
- **멀티 유저 협업**: 동시 드로잉 지원
- **드로잉 내보내기**: 이미지/영상 저장 기능
