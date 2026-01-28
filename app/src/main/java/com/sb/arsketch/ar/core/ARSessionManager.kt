package com.sb.arsketch.ar.core

import android.app.Activity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.SessionPausedException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class ARSessionState {
    object NotInitialized : ARSessionState()
    object Initializing : ARSessionState()
    object Ready : ARSessionState()
    data class Error(val message: String) : ARSessionState()
}

sealed class ARTrackingState {
    object NotTracking : ARTrackingState()
    object Tracking : ARTrackingState()
    object Paused : ARTrackingState()
}

@Singleton
class ARSessionManager @Inject constructor() {

    private var session: Session? = null
    private var isInstallRequested = false
    @Volatile
    private var isResumed = false

    private val _sessionState = MutableStateFlow<ARSessionState>(ARSessionState.NotInitialized)
    val sessionState: StateFlow<ARSessionState> = _sessionState.asStateFlow()

    private val _trackingState = MutableStateFlow<ARTrackingState>(ARTrackingState.NotTracking)
    val trackingState: StateFlow<ARTrackingState> = _trackingState.asStateFlow()

    fun checkAndInitialize(activity: Activity): Boolean {
        _sessionState.value = ARSessionState.Initializing

        val availability = ArCoreApk.getInstance().checkAvailability(activity)

        if (availability.isTransient) {
            return false
        }

        if (availability.isSupported) {
            return tryCreateSession(activity)
        } else {
            _sessionState.value = ARSessionState.Error("ARCore를 지원하지 않는 기기입니다")
            return false
        }
    }

    private fun tryCreateSession(activity: Activity): Boolean {
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, !isInstallRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    isInstallRequested = true
                    false
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    createSession(activity)
                    true
                }
            }
        } catch (e: Exception) {
            handleSessionCreationError(e)
            false
        }
    }

    private fun createSession(activity: Activity) {
        session = Session(activity).also { newSession ->
            val config = Config(newSession).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL

                if (newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }

                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                focusMode = Config.FocusMode.AUTO
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }

            newSession.configure(config)
            _sessionState.value = ARSessionState.Ready
            Timber.d("ARCore 세션 생성 완료")
        }
    }

    private fun handleSessionCreationError(e: Exception) {
        val errorMessage = when (e) {
            is UnavailableArcoreNotInstalledException -> "ARCore가 설치되어 있지 않습니다"
            is UnavailableApkTooOldException -> "ARCore 업데이트가 필요합니다"
            is UnavailableSdkTooOldException -> "앱 업데이트가 필요합니다"
            is UnavailableDeviceNotCompatibleException -> "이 기기는 ARCore를 지원하지 않습니다"
            else -> "AR 세션 생성 실패: ${e.message}"
        }
        _sessionState.value = ARSessionState.Error(errorMessage)
        Timber.e(e, "ARCore 세션 생성 오류")
    }

    fun resume() {
        session?.let { activeSession ->
            try {
                activeSession.resume()
                isResumed = true
                Timber.d("AR 세션 재개")
            } catch (e: CameraNotAvailableException) {
                isResumed = false
                _sessionState.value = ARSessionState.Error("카메라를 사용할 수 없습니다")
                Timber.e(e, "카메라 사용 불가")
            }
        }
    }

    fun pause() {
        isResumed = false
        session?.pause()
        _trackingState.value = ARTrackingState.Paused
        Timber.d("AR 세션 일시 중지")
    }

    fun destroy() {
        isResumed = false
        session?.close()
        session = null
        _sessionState.value = ARSessionState.NotInitialized
        _trackingState.value = ARTrackingState.NotTracking
        Timber.d("AR 세션 종료")
    }

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        session?.setDisplayGeometry(rotation, width, height)
    }

    fun update(): Frame? {
        if (!isResumed) {
            return null
        }
        return try {
            session?.update()?.also { frame ->
                val camera = frame.camera
                _trackingState.value = when (camera.trackingState) {
                    TrackingState.TRACKING -> ARTrackingState.Tracking
                    TrackingState.PAUSED -> ARTrackingState.Paused
                    else -> ARTrackingState.NotTracking
                }
            }
        } catch (e: SessionPausedException) {
            Timber.w("세션이 일시 중지된 상태에서 업데이트 시도")
            isResumed = false
            null
        } catch (e: CameraNotAvailableException) {
            Timber.e(e, "프레임 업데이트 실패")
            null
        }
    }

    fun isReady(): Boolean = session != null && _sessionState.value == ARSessionState.Ready

    fun getSession(): Session? = session
}
