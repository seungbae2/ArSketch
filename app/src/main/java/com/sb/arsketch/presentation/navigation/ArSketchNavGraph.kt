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
import com.sb.arsketch.presentation.connect.ConnectRoute
import com.sb.arsketch.presentation.host.HostRoute
import com.sb.arsketch.presentation.viewer.ViewerRoute
import java.net.URLEncoder

object Routes {
    const val CONNECT = "connect"
    const val HOST = "host/{serverUrl}/{token}"
    const val VIEWER = "viewer/{serverUrl}/{token}"

    fun host(serverUrl: String, token: String): String {
        val encodedUrl = URLEncoder.encode(serverUrl, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return "host/$encodedUrl/$encodedToken"
    }

    fun viewer(serverUrl: String, token: String): String {
        val encodedUrl = URLEncoder.encode(serverUrl, "UTF-8")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return "viewer/$encodedUrl/$encodedToken"
    }
}

@Composable
fun ArSketchNavGraph(
    arSessionManager: ARSessionManager,
    drawingController: DrawingController,
    anchorManager: AnchorManager,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.CONNECT
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 연결 화면
        composable(Routes.CONNECT) {
            ConnectRoute(
                onNavigateToHost = { serverUrl, token ->
                    navController.navigate(Routes.host(serverUrl, token))
                },
                onNavigateToViewer = { serverUrl, token ->
                    navController.navigate(Routes.viewer(serverUrl, token))
                }
            )
        }

        // 호스트 화면 (AR Drawing + Streaming)
        composable(
            route = Routes.HOST,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType }
            )
        ) {
            HostRoute(
                arSessionManager = arSessionManager,
                drawingController = drawingController,
                anchorManager = anchorManager,
                onNavigateBack = {
                    navController.popBackStack(Routes.CONNECT, inclusive = false)
                }
            )
        }

        // 뷰어 화면 (Remote Video + Stroke Overlay)
        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("serverUrl") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType }
            )
        ) {
            ViewerRoute(
                onNavigateBack = {
                    navController.popBackStack(Routes.CONNECT, inclusive = false)
                }
            )
        }
    }
}
