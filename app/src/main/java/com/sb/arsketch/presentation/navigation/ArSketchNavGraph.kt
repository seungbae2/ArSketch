package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sb.arsketch.ar.core.AnchorManager
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.screen.drawing.DrawingRoute
import com.sb.arsketch.presentation.screen.home.HomeScreen
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

/**
 * 네비게이션 라우트 정의
 */
object Routes {
    const val HOME = "home"
    const val DRAWING = "drawing"
    const val STREAMING = "streaming"
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
    startDestination: String = Routes.HOME
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 홈 화면 (모드 선택)
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToDrawing = {
                    navController.navigate(Routes.DRAWING)
                },
                onNavigateToStreaming = {
                    navController.navigate(Routes.STREAMING)
                }
            )
        }

        // 일반 드로잉 화면
        composable(Routes.DRAWING) {
            DrawingRoute(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                isStreamingMode = false,
                sessionIdToLoad = null,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 스트리밍 드로잉 화면
        composable(Routes.STREAMING) {
            DrawingRoute(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                isStreamingMode = true,
                sessionIdToLoad = null,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                },
                onNavigateBack = {
                    navController.popBackStack()
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

            DrawingRoute(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                isStreamingMode = false,
                sessionIdToLoad = sessionId,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                },
                onNavigateBack = {
                    navController.popBackStack()
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
