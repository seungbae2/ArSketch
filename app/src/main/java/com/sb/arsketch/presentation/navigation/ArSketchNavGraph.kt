package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sb.arsketch.ar.core.AnchorManager
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.screen.drawing.DrawingScreen
import com.sb.arsketch.presentation.screen.drawing.DrawingViewModel
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

/**
 * 네비게이션 라우트 정의
 */
object Routes {
    const val DRAWING = "drawing"
    const val DRAWING_WITH_SESSION = "drawing/{sessionId}"
    const val SESSION_LIST = "sessions"

    fun drawingWithSession(sessionId: String) = "drawing/$sessionId"
}

/**
 * 앱 네비게이션 그래프
 */
@Composable
fun ArSketchNavGraph(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    anchorManager: AnchorManager,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DRAWING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 새 드로잉 화면
        composable(Routes.DRAWING) {
            DrawingScreen(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        // 저장된 세션 불러오기
        composable(
            route = Routes.DRAWING_WITH_SESSION,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val viewModel: DrawingViewModel = hiltViewModel()

            // 세션 ID로 불러오기
            LaunchedEffect(sessionId) {
                sessionId?.let { viewModel.loadSession(it) }
            }

            DrawingScreen(
                viewModel = viewModel,
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        // 세션 목록 화면
        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSessionSelected = { sessionId ->
                    navController.navigate(Routes.drawingWithSession(sessionId)) {
                        // 기존 드로잉 화면 제거
                        popUpTo(Routes.DRAWING) { inclusive = true }
                    }
                }
            )
        }
    }
}
