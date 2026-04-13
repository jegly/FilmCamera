package com.jegly.filmcamera

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.jegly.filmcamera.ui.theme.FilmCameraTheme
import com.jegly.filmcamera.ui.viewfinder.ViewfinderScreen
import com.jegly.filmcamera.ui.viewfinder.ViewfinderViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ViewfinderViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilmCameraTheme {
                val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                if (cameraPermissionState.status.isGranted) {
                    ViewfinderScreen(viewModel = viewModel)
                } else {
                    LaunchedEffect(Unit) {
                        cameraPermissionState.launchPermissionRequest()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Camera permission required", color = Color.White)
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            viewModel.capture()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
