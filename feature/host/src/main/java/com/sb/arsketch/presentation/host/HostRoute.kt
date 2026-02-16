package com.sb.arsketch.presentation.host

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

@Composable
fun HostRoute(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    anchorManager: AnchorManager,
    onNavigateBack: () -> Unit,
    viewModel: HostViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted && activity != null) {
            arSessionManager.checkAndInitialize(activity)
        }
    }

    var glSurfaceView: ARGLSurfaceView? by remember { mutableStateOf(null) }

    val sessionState by arSessionManager.sessionState.collectAsStateWithLifecycle()
    val isSessionReady = sessionState == ARSessionState.Ready

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
                viewModel.onAction(HostAction.UpdateARState(ARState.Searching))
            is ARSessionState.Error ->
                viewModel.onAction(
                    HostAction.UpdateARState(
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
            viewModel.onAction(HostAction.UpdateARState(arState))
        }
    }

    // 드로잉 컨트롤러 콜백 설정
    LaunchedEffect(drawingController) {
        drawingController.onStrokeStartWithAnchor = { info ->
            val brush = info.remoteBrush
            if (brush != null) {
                viewModel.onAction(
                    HostAction.RemoteTouchStart(
                        point = info.localPoint,
                        anchorId = info.anchorId,
                        color = brush.color,
                        thickness = brush.thickness,
                        mode = brush.mode
                    )
                )
            } else {
                viewModel.onAction(HostAction.TouchStart(info.localPoint, info.anchorId))
            }
        }
        drawingController.onStrokePoint = { point ->
            viewModel.onAction(HostAction.TouchMove(point))
        }
        drawingController.onStrokeEnd = {
            viewModel.onAction(HostAction.TouchEnd)
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
                is HostEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is HostEvent.Disconnected -> onNavigateBack()
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

    HostScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        hasCameraPermission = hasCameraPermission,
        isSessionReady = isSessionReady,
        onAction = viewModel::onAction,
        arViewFactory = { ctx, onViewCreated ->
            ARGLSurfaceView(ctx, arSessionManager, anchorManager).also { view ->
                glSurfaceView = view
                onViewCreated(view)

                view.onTouchDown = { x, y -> drawingController.onTouchDown(x, y) }
                view.onTouchMove = { x, y -> drawingController.onTouchMove(x, y) }
                view.onTouchUp = { drawingController.onTouchUp() }

                view.getARRenderer().onFrameUpdate = { frame ->
                    drawingController.updateFrame(frame)
                    val (strokes, currentStroke) = viewModel.getStrokesForRendering()
                    view.getARRenderer().updateStrokes(strokes, currentStroke)
                }

                view.post {
                    drawingController.setViewportSize(view.width, view.height)
                    viewModel.setSurfaceView(view)
                }

                // 화면 회전 등으로 뷰 크기가 바뀌면 캡처 재시작
                var lastWidth = 0
                var lastHeight = 0
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val w = v.width
                    val h = v.height
                    if (w > 0 && h > 0 && (w != lastWidth || h != lastHeight)) {
                        lastWidth = w
                        lastHeight = h
                        drawingController.setViewportSize(w, h)
                        viewModel.setSurfaceView(view)
                    }
                }

                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    view.onResume()
                }
            }
        }
    )
}
