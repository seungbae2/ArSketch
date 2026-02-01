package com.sb.arsketch.presentation.screen.drawing

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sb.arsketch.ar.core.AnchorManager
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.ARSessionState
import com.sb.arsketch.ar.core.ARTrackingState
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.state.ARState

/**
 * DrawingScreen의 Container Composable
 * ViewModel, AR 세션 관리, 이벤트 처리를 담당
 */
@Composable
fun DrawingRoute(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    anchorManager: AnchorManager,
    isStreamingMode: Boolean = false,
    sessionIdToLoad: String? = null,
    onNavigateToSessions: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DrawingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 카메라 권한 상태
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted && activity != null) {
            arSessionManager.checkAndInitialize(activity)
        }
    }

    // GLSurfaceView 참조
    var glSurfaceView: ARGLSurfaceView? by remember { mutableStateOf(null) }

    // AR 세션 준비 상태
    val sessionState by arSessionManager.sessionState.collectAsState()
    val isSessionReady = sessionState == ARSessionState.Ready

    // 세션 ID가 있으면 로드
    LaunchedEffect(sessionIdToLoad) {
        sessionIdToLoad?.let {
            viewModel.onAction(DrawingAction.LoadSession(it))
        }
    }

    // 권한 확인 및 요청
    LaunchedEffect(Unit) {
        if (hasCameraPermission) {
            activity?.let { arSessionManager.checkAndInitialize(it) }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // AR 세션 상태 관찰
    LaunchedEffect(sessionState) {
        when (sessionState) {
            is ARSessionState.Ready ->
                viewModel.onAction(DrawingAction.UpdateARState(ARState.Searching))
            is ARSessionState.Error ->
                viewModel.onAction(
                    DrawingAction.UpdateARState(
                        ARState.Error((sessionState as ARSessionState.Error).message)
                    )
                )
            else -> {}
        }
    }

    // 추적 상태 관찰
    LaunchedEffect(arSessionManager) {
        arSessionManager.trackingState.collect { state ->
            val arState = when (state) {
                is ARTrackingState.Tracking -> ARState.Tracking
                is ARTrackingState.NotTracking -> ARState.Searching
                is ARTrackingState.Paused -> ARState.Paused
            }
            viewModel.onAction(DrawingAction.UpdateARState(arState))
        }
    }

    // 드로잉 컨트롤러 콜백 설정
    LaunchedEffect(drawingController) {
        drawingController.onStrokeStartWithAnchor = { info ->
            viewModel.onAction(DrawingAction.TouchStart(info.localPoint, info.anchorId))
        }
        drawingController.onStrokePoint = { point ->
            viewModel.onAction(DrawingAction.TouchMove(point))
        }
        drawingController.onStrokeEnd = {
            viewModel.onAction(DrawingAction.TouchEnd)
        }
    }

    // 드로잉 모드 동기화
    LaunchedEffect(uiState.drawingMode) {
        drawingController.setDrawingMode(uiState.drawingMode)
    }

    // 평면 표시 상태 동기화
    LaunchedEffect(uiState.showPlanes) {
        glSurfaceView?.getARRenderer()?.showPlanes = uiState.showPlanes
    }

    // 이벤트 수집
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DrawingEvent.ShowSnackbar ->
                    snackbarHostState.showSnackbar(event.message)
                is DrawingEvent.SessionSaved ->
                    snackbarHostState.showSnackbar("'${event.sessionName}' 저장됨")
                is DrawingEvent.SessionLoaded ->
                    snackbarHostState.showSnackbar(
                        "'${event.sessionName}' 불러옴 (${event.strokeCount}개 스트로크)"
                    )
                is DrawingEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // 라이프사이클 관찰
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView?.release()
        }
    }

    DrawingScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        hasCameraPermission = hasCameraPermission,
        isSessionReady = isSessionReady,
        isStreamingMode = isStreamingMode,
        onAction = viewModel::onAction,
        onNavigateToSessions = onNavigateToSessions,
        onNavigateBack = onNavigateBack,
        arViewFactory = { ctx, onViewCreated ->
            ARGLSurfaceView(ctx, arSessionManager, anchorManager).also { view ->
                glSurfaceView = view
                onViewCreated(view)

                // 터치 이벤트 연결
                view.onTouchDown = { x, y ->
                    drawingController.onTouchDown(x, y)
                }
                view.onTouchMove = { x, y ->
                    drawingController.onTouchMove(x, y)
                }
                view.onTouchUp = {
                    drawingController.onTouchUp()
                }

                // 프레임 콜백 설정
                view.getARRenderer().onFrameUpdate = { frame ->
                    drawingController.updateFrame(frame)
                    val (strokes, currentStroke) = viewModel.getStrokesForRendering()
                    view.getARRenderer().updateStrokes(strokes, currentStroke)
                }

                // 뷰포트 크기 설정
                view.post {
                    drawingController.setViewportSize(view.width, view.height)
                }

                // Activity가 이미 RESUMED 상태면 즉시 resume 호출
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    view.onResume()
                }
            }
        }
    )
}
