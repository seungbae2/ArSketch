package com.sb.arsketch.presentation.screen.drawing

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sb.arsketch.ar.core.ARGLSurfaceView
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.ARSessionState
import com.sb.arsketch.ar.core.ARTrackingState
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.domain.model.DrawingMode
import com.sb.arsketch.presentation.component.ActionToolbar
import com.sb.arsketch.presentation.component.BrushToolbar
import com.sb.arsketch.presentation.component.DepthSlider
import com.sb.arsketch.presentation.component.DrawingModeToggle
import com.sb.arsketch.presentation.component.SaveSessionDialog
import com.sb.arsketch.presentation.component.TrackingStatusIndicator
import com.sb.arsketch.presentation.state.ARState
import timber.log.Timber

/**
 * 메인 드로잉 화면
 */
@Composable
fun DrawingScreen(
    viewModel: DrawingViewModel = hiltViewModel(),
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    onNavigateToSessions: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()

    // 카메라 권한 상태
    var hasCameraPermission by remember { mutableStateOf(false) }

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

    // 권한 요청
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // AR 세션 상태 관찰
    LaunchedEffect(arSessionManager) {
        arSessionManager.sessionState.collect { state ->
            when (state) {
                is ARSessionState.Ready -> viewModel.updateARState(ARState.Searching)
                is ARSessionState.Error -> viewModel.updateARState(ARState.Error(state.message))
                else -> {}
            }
        }
    }

    // 추적 상태 관찰
    LaunchedEffect(arSessionManager) {
        arSessionManager.trackingState.collect { state ->
            when (state) {
                is ARTrackingState.Tracking ->
                    viewModel.updateARState(ARState.Tracking)
                is ARTrackingState.NotTracking ->
                    viewModel.updateARState(ARState.Searching)
                is ARTrackingState.Paused ->
                    viewModel.updateARState(ARState.Paused)
            }
        }
    }

    // 드로잉 컨트롤러 콜백 설정
    LaunchedEffect(drawingController) {
        drawingController.onStrokeStart = { point ->
            viewModel.onTouchStart(point)
        }
        drawingController.onStrokePoint = { point ->
            viewModel.onTouchMove(point)
        }
        drawingController.onStrokeEnd = {
            viewModel.onTouchEnd()
        }
    }

    // 드로잉 모드 동기화
    LaunchedEffect(uiState.drawingMode) {
        drawingController.setDrawingMode(uiState.drawingMode)
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

    // UI
    Box(modifier = Modifier.fillMaxSize()) {
        // AR GLSurfaceView
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    ARGLSurfaceView(ctx, arSessionManager).also { view ->
                        glSurfaceView = view

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

                            // 렌더러에 스트로크 데이터 전달
                            val (strokes, currentStroke) = viewModel.getStrokesForRendering()
                            view.getARRenderer().updateStrokes(strokes, currentStroke)
                        }

                        // 뷰포트 크기 설정 (레이아웃 후)
                        view.post {
                            drawingController.setViewportSize(view.width, view.height)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 상단 상태 표시 및 모드 토글
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TrackingStatusIndicator(arState = uiState.arState)

            Spacer(modifier = Modifier.height(8.dp))

            DrawingModeToggle(
                currentMode = uiState.drawingMode,
                onModeChange = viewModel::setDrawingMode
            )

            // Air 모드일 때만 깊이 슬라이더 표시
            if (uiState.drawingMode == DrawingMode.AIR) {
                Spacer(modifier = Modifier.height(8.dp))
                DepthSlider(
                    depth = uiState.airDrawingDepth,
                    onDepthChange = viewModel::setAirDrawingDepth,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // 하단 툴바
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            BrushToolbar(
                brushSettings = uiState.brushSettings,
                onColorSelected = viewModel::setColor,
                onThicknessSelected = viewModel::setThickness
            )

            Spacer(modifier = Modifier.height(8.dp))

            ActionToolbar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onClear = viewModel::clearAll,
                onSave = viewModel::showSaveDialog,
                onShowSessions = onNavigateToSessions
            )
        }

        // 에러 메시지
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 140.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(error)
            }
        }

        // 저장 다이얼로그
        if (uiState.showSaveDialog) {
            SaveSessionDialog(
                sessionName = uiState.sessionName,
                onSessionNameChange = viewModel::updateSessionName,
                onDismiss = viewModel::dismissSaveDialog,
                onConfirm = viewModel::saveSession
            )
        }
    }
}
