package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.screen.drawing.DrawingScreen
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

object Routes {
    const val DRAWING = "drawing"
    const val SESSION_LIST = "sessions"
}

@Composable
fun ArSketchNavGraph(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DRAWING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.DRAWING) {
            DrawingScreen(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                onNavigateToSessions = {
                    navController.navigate(Routes.SESSION_LIST)
                }
            )
        }

        composable(Routes.SESSION_LIST) {
            SessionListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSessionSelected = { sessionId ->
                    navController.popBackStack()
                }
            )
        }
    }
}
