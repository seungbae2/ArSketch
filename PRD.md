# AirSketch AR – PRD

## 1. 제품 개요

**AirSketch AR**는 모바일 디바이스를 통해 현실 공간에 3D 드로잉을 그릴 수 있는 AR 애플리케이션이다.  
사용자는 실제 바닥/테이블과 같은 표면 위에 선을 그리거나, 공중에 자유롭게 드로잉할 수 있으며, 드로잉 결과는 Stroke 단위로 관리 및 저장된다.

본 프로젝트는 **ARCore 기반 실시간 입력 처리, 3D 좌표 변환, 렌더링 최적화 경험을 증빙**하기 위한 포트폴리오용 앱이다.

---

## 2. 문제 정의 (Why AR Drawing?)

- 기존 드로잉 앱은 2D 평면에 국한됨
- AR Drawing은 다음 기술 과제를 포함
  - 스크린 터치 → 월드 좌표 변환
  - 공간 인식 + 실시간 렌더링
  - 성능 최적화
- AR 엔지니어링 실무 역량을 가장 직관적으로 보여줄 수 있는 문제

---

## 3. 핵심 목표 (Portfolio Proof Points)

이 앱을 통해 아래 역량을 명확히 증명한다:

1. ARCore Raycast / HitTest 이해
2. 2D 입력 → 3D World Coordinate 변환
3. 실시간 Polyline(Line Strip) 생성 및 업데이트
4. 성능 최적화
   - 포인트 리샘플링
   - 메쉬 업데이트 쓰로틀링
5. AR Scene ↔ UI State 아키텍처 분리
6. Stroke 단위 Undo / Redo / Persistence

---

## 4. 타겟 플랫폼 & 기술 스택

- Platform: Android
- Language: Kotlin
- UI: Jetpack Compose
- AR: ARCore
- Rendering: Line Strip 기반 Polyline Mesh
- Architecture: Clean Architecture
- State: StateFlow
- Storage: Room 또는 JSON File

---

## 5. 주요 기능 정의

### 5.1 MVP 기능 (필수)

#### AR 환경
- Plane Detection (바닥 / 테이블)
- Tracking 상태 표시 (Initializing / Tracking)

#### Drawing
- Surface Drawing 모드
  - 화면 터치 → Raycast → Plane Hit Point
  - 월드 좌표 기반 선 그리기
- Stroke 단위 관리
  - Start / Add / End
- 브러시
  - 색상 선택 (최소 6개)
  - 두께 선택 (3단계)
- Undo / Redo
- Clear All

#### 데이터
- 드로잉 세션 저장 / 불러오기
- Stroke 리스트 직렬화

---

### 5.2 Plus 기능 (선택)

- Air Drawing 모드
  - Plane hit 실패 시
  - 카메라 forward 방향 일정 거리(depth)에서 포인트 생성
- 간단한 Occlusion 또는 Depth 느낌 표현

---

## 6. 사용자 플로우

1. 앱 실행
2. AR Session 초기화
3. Plane 감지 완료
4. 화면 터치 시작
5. 드로잉 진행 (실시간 선 생성)
6. 손을 떼면 Stroke 종료
7. Undo / Redo
8. 저장 → 재실행 → 불러오기

---

## 7. 렌더링 전략 (Option A – Line Strip)

- 각 Stroke는 연속된 Polyline Mesh로 표현
- 포인트 리스트를 기반으로 Line Strip 생성
- 성능 최적화
  - 거리 기반 포인트 추가
  - 메쉬 업데이트 쓰로틀링
- Sphere 나열 방식 대비
  - 시각적으로 깔끔
  - 실무 AR 구현에 가까움

---

## 8. 성능 최적화 전략

- Distance-based resampling
  - 마지막 포인트와 일정 거리 이상일 때만 추가
- Mesh update throttling
  - 매 이벤트마다 메쉬 갱신하지 않음
  - 16~33ms 단위로 제한
- 포인트 최대 개수 제한

---

## 9. 성공 기준 (Done Definition)

- 드로잉 중 프레임 드랍 없이 안정적 동작
- Surface / Air Drawing 정상 동작
- Undo / Redo 정확히 작동
- 세션 저장 후 재실행 시 복원 가능
- README에서 AR 파이프라인 설명 가능
