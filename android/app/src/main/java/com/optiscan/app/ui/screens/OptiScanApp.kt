package com.optiscan.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.ui.theme.OptiScanTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.optiscan.app.ui.components.HapticType
import com.optiscan.app.ui.components.LocalAccessibleAnnouncer
import com.optiscan.app.ui.components.LocalHapticFeedbackHandler
import com.optiscan.app.ui.components.LocalTripleTapModeEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Root Composable orchestrating state-driven navigation across all 9 OptiScan screens.
 */
@Composable
fun OptiScanApp(
    viewModel: MainViewModel,
    onRequestCameraPermission: () -> Unit,
    onBindCamera: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val capturedBitmap by viewModel.capturedBitmap.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()

    fun performHaptic(type: HapticType = HapticType.CONFIRMED_ACTION) {
        if (settings.hapticFeedbackEnabled) {
            try {
                val (durationMs, amplitude) = when (type) {
                    HapticType.SUBTLE_TICK -> Pair(20L, 50)
                    HapticType.DOUBLE_TICK -> Pair(35L, 120)
                    HapticType.CONFIRMED_ACTION -> Pair(55L, 255)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                }
            } catch (_: Exception) {}
        }
    }

    // Intercept hardware and gesture back button
    BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Welcome) {
        viewModel.navigateBack()
    }

    CompositionLocalProvider(
        LocalAccessibleAnnouncer provides { text -> viewModel.speak(text) },
        LocalHapticFeedbackHandler provides { type -> performHaptic(type) },
        LocalTripleTapModeEnabled provides settings.tripleTapNavigationEnabled
    ) {
        OptiScanTheme(textScale = settings.textSizeScale) {
            when (val screen = currentScreen) {
                is Screen.Welcome -> {
                    WelcomeScreen(
                        onGetStarted = {
                            performHaptic()
                            viewModel.navigateTo(Screen.Home, clearBackstack = true)
                        },
                        onSpeakIntro = {
                            viewModel.speak("Welcome to OptiScan. An accessible visual assistant for reading text and exploring your surroundings. Tap Get Started to begin.")
                        },
                        modifier = modifier.fillMaxSize()
                    )
                }

                is Screen.Home -> {
                    HomeScreen(
                        onNavigateToCamera = { task ->
                            performHaptic()
                            viewModel.navigateTo(Screen.Camera(task))
                        },
                        onNavigateToAskQuestion = {
                            performHaptic()
                            viewModel.navigateTo(Screen.AskQuestion())
                        },
                        onNavigateToSettings = {
                            performHaptic()
                            viewModel.navigateTo(Screen.Settings)
                        },
                        onAnnounceHome = {
                            viewModel.speak("OptiScan Home. Select Read Text, Describe Surroundings, or Ask a Question.", isScreenAnnouncement = true)
                        },
                        modifier = modifier.fillMaxSize()
                    )
                }

            is Screen.Camera -> {
                CameraScreen(
                    task = screen.task,
                    hasCameraPermission = hasCameraPermission,
                    onRequestCameraPermission = onRequestCameraPermission,
                    onPreviewReady = onBindCamera,
                    onCaptureClicked = {
                        performHaptic(HapticType.CONFIRMED_ACTION)
                        viewModel.capturePhoto(context, screen.task)
                    },
                    onBackClick = {
                        viewModel.navigateBack()
                    },
                    onInstructionSpoken = {
                        viewModel.speak("Point camera at object. Press volume button, tap viewfinder, or tap Capture Photo.", isScreenAnnouncement = true)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.ImagePreview -> {
                ImagePreviewScreen(
                    task = screen.task,
                    bitmap = capturedBitmap,
                    onUsePhoto = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Processing(screen.task, screen.imageBytes))
                    },
                    onRetakePhoto = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(screen.task))
                    },
                    onAnnounce = {
                        viewModel.speak("Photo captured. Tap Use This Photo to analyze, or Retake Photo to try again.")
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.Processing -> {
                ProcessingScreen(
                    task = screen.task,
                    isProcessing = isAnalyzing,
                    errorMessage = analysisError,
                    onStartAnalysis = {
                        viewModel.analyzeCapturedImage(screen.task, screen.imageBytes)
                    },
                    onRetry = {
                        viewModel.analyzeCapturedImage(screen.task, screen.imageBytes)
                    },
                    onCancel = {
                        viewModel.cancelAnalysis()
                        viewModel.navigateTo(Screen.Camera(screen.task))
                    },
                    onAnnounceProcessing = {
                        val taskName = if (screen.task == ScanTask.READ_TEXT) "reading text" else "describing surroundings"
                        viewModel.speak("Analyzing your image for $taskName. Please hold steady.")
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.ReadTextResult -> {
                ReadTextResultScreen(
                    text = screen.text,
                    shouldRecapture = screen.shouldRecapture,
                    recaptureReason = screen.recaptureReason,
                    isLiveResult = screen.isLiveResult,
                    isSpeaking = isSpeaking,
                    onReadAloud = { txt -> viewModel.speak(txt) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onAskQuestion = { contextText ->
                        performHaptic()
                        viewModel.navigateTo(Screen.AskQuestion(contextTitle = "Medicine Label", contextContent = contextText))
                    },
                    onCaptureAnother = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(ScanTask.READ_TEXT))
                    },
                    onBackToHome = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Home, clearBackstack = true)
                    },
                    onAnnounceResult = { announcement ->
                        viewModel.speak(announcement)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.DescriptionResult -> {
                DescriptionResultScreen(
                    description = screen.description,
                    shouldRecapture = screen.shouldRecapture,
                    recaptureReason = screen.recaptureReason,
                    isLiveResult = screen.isLiveResult,
                    isSpeaking = isSpeaking,
                    onReadAloud = { desc -> viewModel.speak(desc) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onAskQuestion = { contextText ->
                        performHaptic()
                        viewModel.navigateTo(Screen.AskQuestion(contextTitle = "Indoor Hallway", contextContent = contextText))
                    },
                    onCaptureAnother = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Camera(ScanTask.DESCRIBE_SURROUNDINGS))
                    },
                    onBackToHome = {
                        performHaptic()
                        viewModel.navigateTo(Screen.Home, clearBackstack = true)
                    },
                    onAnnounceResult = { announcement ->
                        viewModel.speak(announcement)
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.AskQuestion -> {
                AskQuestionScreen(
                    contextContent = screen.contextContent,
                    isSpeaking = isSpeaking,
                    onSpeak = { query -> viewModel.speak(query) },
                    onStopSpeech = { viewModel.stopSpeech() },
                    onSubmitQuestion = { query, onAnswer ->
                        viewModel.askQuestion(query, screen.contextContent, onAnswer)
                    },
                    onBackClick = { viewModel.navigateBack() },
                    onAnnounceScreen = {
                        viewModel.speak("Ask a Question. Tap the microphone to speak, or type your question below.")
                    },
                    modifier = modifier.fillMaxSize()
                )
            }

            is Screen.Settings -> {
                SettingsScreen(
                    settings = settings,
                    onUpdateSettings = { newSettings ->
                        performHaptic()
                        viewModel.updateSettings(newSettings)
                    },
                    onTestSpeechRate = { rate ->
                        viewModel.testSpeechRate(rate)
                    },
                    onBackClick = { viewModel.navigateBack() },
                    onAnnounceSettings = {
                        viewModel.speak("Settings. Adjust voice playback speed, feedback options, and text scale.")
                    },
                    modifier = modifier.fillMaxSize()
                )
            }
        }
    }
}
}

