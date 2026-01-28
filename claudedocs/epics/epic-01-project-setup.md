# Epic 1: 프로젝트 설정

## 개요
- **목표**: ARCore, Room, Hilt 등 필요한 의존성 추가 및 프로젝트 기본 구조 설정
- **예상 작업량**: 중간
- **의존성**: 없음 (첫 번째 Epic)

---

## 작업 목록

### Task 1.1: 버전 카탈로그 업데이트
**파일**: `gradle/libs.versions.toml`

**추가할 버전**:
```toml
[versions]
arcore = "1.42.0"
room = "2.6.1"
hilt = "2.51"
hiltNavigationCompose = "1.2.0"
navigationCompose = "2.7.7"
timber = "5.0.1"
kotlinxSerialization = "1.6.3"
ksp = "2.0.21-1.0.27"
```

**추가할 라이브러리**:
```toml
[libraries]
# ARCore
arcore = { module = "com.google.ar:core", version.ref = "arcore" }

# Room
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

# Hilt
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Navigation
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }

# Timber
timber = { module = "com.jakewharton.timber:timber", version.ref = "timber" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

**추가할 플러그인**:
```toml
[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

---

### Task 1.2: Root build.gradle.kts 수정
**파일**: `build.gradle.kts` (루트)

**변경 사항**:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

---

### Task 1.3: App build.gradle.kts 수정
**파일**: `app/build.gradle.kts`

**플러그인 추가**:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}
```

**의존성 추가**:
```kotlin
dependencies {
    // ARCore
    implementation(libs.arcore)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Timber
    implementation(libs.timber)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // 기존 의존성 유지...
}
```

---

### Task 1.4: AndroidManifest.xml 수정
**파일**: `app/src/main/AndroidManifest.xml`

**추가할 권한**:
```xml
<uses-permission android:name="android.permission.CAMERA" />
```

**추가할 기능 요구사항**:
```xml
<uses-feature android:name="android.hardware.camera.ar" android:required="true" />
<uses-feature android:glEsVersion="0x00030000" android:required="true" />
```

**Application 태그 내 ARCore 메타데이터**:
```xml
<meta-data
    android:name="com.google.ar.core"
    android:value="required" />
```

> **참고**: `ar_required`는 ARCore 필수 앱, `ar_optional`은 선택적 AR 기능 앱에 사용

---

### Task 1.5: Application 클래스 생성
**파일**: `app/src/main/java/com/sb/arsketch/ArSketchApplication.kt`

```kotlin
package com.sb.arsketch

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ArSketchApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Timber 초기화 (디버그 빌드에서만)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
```

**Manifest 업데이트**:
```xml
<application
    android:name=".ArSketchApplication"
    ... >
```

---

### Task 1.6: MainActivity 수정
**파일**: `app/src/main/java/com/sb/arsketch/MainActivity.kt`

```kotlin
package com.sb.arsketch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.sb.arsketch.ui.theme.ArSketchTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArSketchTheme {
                // TODO: NavHost 추가 예정
            }
        }
    }
}
```

---

### Task 1.7: 패키지 구조 생성
**생성할 디렉토리**:

```
app/src/main/java/com/sb/arsketch/
├── di/
├── data/
│   ├── local/
│   │   ├── db/
│   │   └── entity/
│   ├── repository/
│   └── mapper/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│       ├── stroke/
│       └── session/
├── presentation/
│   ├── navigation/
│   ├── screen/
│   │   ├── drawing/
│   │   └── sessions/
│   ├── component/
│   └── state/
├── ar/
│   ├── core/
│   ├── renderer/
│   ├── geometry/
│   └── util/
└── util/
```

---

## 완료 조건

- [ ] `./gradlew assembleDebug` 빌드 성공
- [ ] Hilt Application 클래스 정상 등록
- [ ] ARCore 메타데이터 Manifest에 추가됨
- [ ] Camera 권한 선언됨
- [ ] 모든 패키지 디렉토리 생성됨
- [ ] Timber 초기화 및 로그 출력 확인

---

## 참고 자료

- [ARCore 시작하기](https://developers.google.com/ar/develop/java/quickstart)
- [Hilt 설정 가이드](https://developer.android.com/training/dependency-injection/hilt-android)
- [Room 설정 가이드](https://developer.android.com/training/data-storage/room)

---

## 다음 단계

→ [Epic 2: Domain Layer](epic-02-domain-layer.md)
