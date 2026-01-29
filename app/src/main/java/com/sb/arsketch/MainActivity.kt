package com.sb.arsketch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sb.arsketch.ar.core.AnchorManager
import com.sb.arsketch.ar.core.ARSessionManager
import com.sb.arsketch.ar.core.DrawingController
import com.sb.arsketch.presentation.navigation.ArSketchNavGraph
import com.sb.arsketch.ui.theme.ArSketchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var arSessionManager: ARSessionManager

    @Inject
    lateinit var drawingController: DrawingController

    @Inject
    lateinit var anchorManager: AnchorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // AR 세션 초기화는 권한 획득 후 DrawingScreen에서 수행

        setContent {
            ArSketchTheme {
                ArSketchNavGraph(
                    arSessionManager = arSessionManager,
                    drawingController = drawingController,
                    anchorManager = anchorManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        anchorManager.releaseAll()
        arSessionManager.destroy()
    }
}
