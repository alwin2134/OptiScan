package com.optiscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.optiscan.app.speech.TtsManager
import com.optiscan.app.ui.screens.HomeScreen
import com.optiscan.app.ui.screens.HomeScreenViewModel
import com.optiscan.app.ui.theme.OptiScanTheme

/**
 * Main application activity initializing permissions, TTS, and the Compose UI root.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: HomeScreenViewModel by viewModels()
    private lateinit var ttsManager: TtsManager
    private var activePreviewView: PreviewView? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                activePreviewView?.let { bindCamera(it) }
                viewModel.resetToIdle()
            } else {
                viewModel.setPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize TTS with accessible welcome announcement
        ttsManager = TtsManager(this) { isSuccess ->
            if (isSuccess) {
                ttsManager.speak("OptiScan ready. Select Read Text or Describe Scene.")
            }
        }
        viewModel.attachTtsManager(ttsManager)

        setContent {
            OptiScanTheme {
                HomeScreen(
                    viewModel = viewModel,
                    onRequestCameraPermission = { checkAndRequestCameraPermission() },
                    onPreviewReady = { previewView ->
                        activePreviewView = previewView
                        if (hasCameraPermission()) {
                            bindCamera(previewView)
                        } else {
                            checkAndRequestCameraPermission()
                        }
                    }
                )
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestCameraPermission() {
        if (hasCameraPermission()) {
            activePreviewView?.let { bindCamera(it) }
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        viewModel.cameraManager.startCamera(
            context = this,
            lifecycleOwner = this,
            previewView = previewView
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager.shutdown()
    }
}
