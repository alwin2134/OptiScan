package com.optiscan.app.ui.screens

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.speech.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for OptiScan navigation, camera state, and accessibility settings.
 */
class MainViewModel(
    val cameraManager: CameraManager = CameraManager()
) : ViewModel() {

    private var ttsManager: TtsManager? = null

    // Navigation Stack
    private val backStack = mutableListOf<Screen>()
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Welcome)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Camera State
    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    // Speech State
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    fun attachTtsManager(tts: TtsManager) {
        this.ttsManager = tts
        tts.onSpeechStateChanged = { speaking ->
            _isSpeaking.value = speaking
        }
        applySettingsToTts(_settings.value)
    }

    fun setCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
    }

    // Navigation methods
    fun navigateTo(screen: Screen, clearBackstack: Boolean = false) {
        if (clearBackstack) {
            backStack.clear()
        } else {
            backStack.add(_currentScreen.value)
        }
        _currentScreen.value = screen
    }

    fun navigateBack(): Boolean {
        stopSpeech()
        return if (backStack.isNotEmpty()) {
            val prev = backStack.removeAt(backStack.size - 1)
            _currentScreen.value = prev
            true
        } else if (_currentScreen.value !is Screen.Home && _currentScreen.value !is Screen.Welcome) {
            _currentScreen.value = Screen.Home
            true
        } else {
            false
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        applySettingsToTts(newSettings)
    }

    private fun applySettingsToTts(appSettings: AppSettings) {
        ttsManager?.voiceFeedbackEnabled = appSettings.voiceFeedbackEnabled
        ttsManager?.setSpeechRate(appSettings.speechRate)
    }

    // Spoken feedback methods
    fun speak(text: String) {
        if (_settings.value.voiceFeedbackEnabled) {
            ttsManager?.speak(text)
        }
    }

    fun stopSpeech() {
        ttsManager?.stop()
        _isSpeaking.value = false
    }

    fun testSpeechRate(rate: Float) {
        ttsManager?.setSpeechRate(rate)
        speak("This is a sample sentence spoken at ${String.format("%.2f", rate)} speed multiplier.")
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.unbind()
        ttsManager?.shutdown()
    }
}
