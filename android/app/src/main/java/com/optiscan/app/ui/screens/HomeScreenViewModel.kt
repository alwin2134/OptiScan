package com.optiscan.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.models.StatusTypes
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.repository.ScanRepository
import com.optiscan.app.data.repository.ScanRepositoryImpl
import com.optiscan.app.navigation.ScreenState
import com.optiscan.app.speech.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing the main OptiScan interaction loop.
 */
class HomeScreenViewModel(
    val cameraManager: CameraManager = CameraManager(),
    private val repository: ScanRepository = ScanRepositoryImpl(),
    private var ttsManager: TtsManager? = null
) : ViewModel() {

    private val tag = "HomeScreenViewModel"

    private val _uiState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val uiState: StateFlow<ScreenState> = _uiState.asStateFlow()

    private var lastSpokenText: String = ""

    fun attachTtsManager(tts: TtsManager) {
        this.ttsManager = tts
    }

    fun setPermissionDenied() {
        _uiState.value = ScreenState.PermissionDenied
        speak("Camera permission is required to use OptiScan.")
    }

    fun resetToIdle() {
        ttsManager?.stop()
        _uiState.value = ScreenState.Idle
    }

    /**
     * Workflow 1: Read printed text using on-device ML Kit OCR.
     */
    fun onReadTextClicked(context: Context) {
        viewModelScope.launch {
            _uiState.value = ScreenState.Capturing
            speak("Capturing photo for text reading.")

            val captureResult = cameraManager.capturePhoto(context)
            if (captureResult.isFailure) {
                val err = captureResult.exceptionOrNull()?.message ?: "Camera capture failed."
                _uiState.value = ScreenState.Error(err)
                speak("Failed to capture image: $err")
                return@launch
            }

            val imageBytes = captureResult.getOrThrow()
            val bitmap = cameraManager.bytesToBitmap(imageBytes)

            if (bitmap == null) {
                _uiState.value = ScreenState.Error("Unable to decode captured photo.")
                speak("Unable to process captured photo.")
                return@launch
            }

            _uiState.value = ScreenState.Processing("Reading printed text...")
            speak("Reading text.")

            val ocrResult = repository.readTextOnDevice(bitmap)
            ocrResult.onSuccess { text ->
                lastSpokenText = text
                _uiState.value = ScreenState.Result(
                    taskName = "Printed Text",
                    text = text,
                    shouldRecapture = false
                )
                speak(text)
            }.onFailure { ex ->
                val errorMsg = "OCR failed: ${ex.message}"
                _uiState.value = ScreenState.Error(errorMsg)
                speak(errorMsg)
            }
        }
    }

    /**
     * Workflow 2: Describe surroundings using backend vision provider.
     */
    fun onDescribeImageClicked(context: Context) {
        viewModelScope.launch {
            _uiState.value = ScreenState.Capturing
            speak("Capturing photo to describe scene.")

            val captureResult = cameraManager.capturePhoto(context)
            if (captureResult.isFailure) {
                val err = captureResult.exceptionOrNull()?.message ?: "Camera capture failed."
                _uiState.value = ScreenState.Error(err)
                speak("Failed to capture image: $err")
                return@launch
            }

            val imageBytes = captureResult.getOrThrow()
            _uiState.value = ScreenState.Processing("Analyzing surroundings...")
            speak("Analyzing scene, please wait.")

            val result = repository.analyzeImage(imageBytes, TaskTypes.DESCRIBE_IMAGE)
            result.onSuccess { response ->
                if (response.shouldRecapture) {
                    val reason = response.reason ?: "Image is too blurry. Please retake."
                    lastSpokenText = "Recapture recommended. $reason"
                    _uiState.value = ScreenState.Result(
                        taskName = "Scene Description",
                        text = "",
                        shouldRecapture = true,
                        recaptureReason = reason
                    )
                    speak(lastSpokenText)
                } else if (response.status == StatusTypes.SUCCESS && response.answer != null) {
                    lastSpokenText = response.answer
                    _uiState.value = ScreenState.Result(
                        taskName = "Scene Description",
                        text = response.answer,
                        shouldRecapture = false
                    )
                    speak(response.answer)
                } else {
                    val errMsg = response.reason ?: "Image analysis returned an error."
                    _uiState.value = ScreenState.Error(errMsg)
                    speak(errMsg)
                }
            }.onFailure { ex ->
                val errMsg = "Could not reach backend service. You can enable offline mock mode in settings."
                Log.e(tag, "Backend analysis request failed", ex)
                _uiState.value = ScreenState.Error(errMsg)
                speak(errMsg)
            }
        }
    }

    /**
     * Repeat the last spoken result aloud for the user.
     */
    fun repeatSpeech() {
        if (lastSpokenText.isNotBlank()) {
            speak(lastSpokenText)
        } else {
            speak("No current result to repeat.")
        }
    }

    private fun speak(text: String) {
        ttsManager?.speak(text)
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.unbind()
        ttsManager?.shutdown()
    }
}
