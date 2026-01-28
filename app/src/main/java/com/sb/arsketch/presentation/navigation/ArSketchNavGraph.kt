package com.sb.arsketch.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sb.arsketch.presentation.screen.drawing.DrawingScreen
import com.sb.arsketch.presentation.screen.sessions.SessionListScreen

object Routes {
    const val DRAWING = "drawing"
    const val SESSION_LIST = "sessions"
}

@Composable
fun ArSketchNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DRAWING
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.DRAWING) {
            DrawingScreen(
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
