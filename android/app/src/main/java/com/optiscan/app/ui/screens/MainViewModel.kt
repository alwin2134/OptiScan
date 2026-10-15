package com.optiscan.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.app.camera.CameraManager
import com.optiscan.app.data.mock.MockData
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.data.repository.ScanRepository
import com.optiscan.app.data.repository.ScanRepositoryImpl
import com.optiscan.app.data.preferences.PreferencesManager
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.ScanTask
import com.optiscan.app.navigation.Screen
import com.optiscan.app.speech.TtsManager
import com.optiscan.app.ui.localization.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for OptiScan navigation, camera state, visual analysis, accessibility, and volume key control.
 */
class MainViewModel(
    val cameraManager: CameraManager = CameraManager(),
    val repository: ScanRepository = ScanRepositoryImpl(),
    private var preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val tag = "MainViewModel"
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

    private val _capturedBytes = MutableStateFlow<ByteArray?>(null)
    val capturedBytes: StateFlow<ByteArray?> = _capturedBytes.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    // Speech State
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // TalkBack / Screen Reader State
    private val _isTalkBackActive = MutableStateFlow(false)
    val isTalkBackActive: StateFlow<Boolean> = _isTalkBackActive.asStateFlow()

    // Analysis State
    private var analysisJob: Job? = null
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    init {
        val loadedSettings = preferencesManager?.loadSettings() ?: AppSettings()
        _settings.value = loadedSettings
        repository.setMockMode(loadedSettings.useMockBackend)
        if (loadedSettings.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(loadedSettings.customBackendUrl)
        }
    }

    fun attachPreferencesManager(prefs: PreferencesManager) {
        this.preferencesManager = prefs
        val loaded = prefs.loadSettings()
        _settings.value = loaded
        repository.setMockMode(loaded.useMockBackend)
        if (loaded.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(loaded.customBackendUrl)
        }
        applySettingsToTts(loaded)
    }

    fun attachTtsManager(tts: TtsManager) {
        this.ttsManager = tts
        tts.onSpeechStateChanged = { speaking ->
            _isSpeaking.value = speaking
        }
        tts.onLanguageFallback = { lang ->
            Log.w(tag, "TTS language fallback occurred for $lang")
        }
        applySettingsToTts(_settings.value)
    }

    fun initAccessibility(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        _isTalkBackActive.value = am?.isTouchExplorationEnabled == true
        am?.addTouchExplorationStateChangeListener { enabled ->
            _isTalkBackActive.value = enabled
        }
    }

    fun setCameraPermission(granted: Boolean) {
        _hasCameraPermission.value = granted
    }

    fun setCapturedBitmap(bitmap: Bitmap?) {
        _capturedBitmap.value = bitmap
    }

    fun setCapturedImage(bitmap: Bitmap?, bytes: ByteArray?) {
        _capturedBitmap.value = bitmap
        _capturedBytes.value = bytes
    }

    // Navigation methods
    fun navigateTo(screen: Screen, clearBackstack: Boolean = false) {
        if (screen is Screen.Camera || screen is Screen.Home || screen is Screen.Settings || screen is Screen.ImagePreview) {
            stopSpeech()
        }
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

    /**
     * Executes photo capture from CameraX and moves to ImagePreview.
     */
    fun capturePhoto(context: Context, task: ScanTask) {
        viewModelScope.launch {
            val isHindi = _settings.value.language == AppLanguage.HINDI
            speak(if (isHindi) "फोटो खींच रहे हैं। कृपया फोन स्थिर रखें।" else "Capturing photo. Please hold steady.")
            if (_hasCameraPermission.value) {
                val result = cameraManager.capturePhoto(context)
                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    val bmp = bytes?.let { cameraManager.bytesToBitmap(it) }
                    setCapturedImage(bmp, bytes)
                    navigateTo(Screen.ImagePreview(task, bytes))
                } else {
                    setCapturedImage(null, null)
                    navigateTo(Screen.ImagePreview(task))
                }
            } else {
                setCapturedImage(null, null)
                navigateTo(Screen.ImagePreview(task))
            }
        }
    }

    /**
     * Hardware Volume Key controller allowing blind users to operate the app physically.
     */
    fun handleVolumeKey(context: Context, keyCode: Int): Boolean {
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (!isVolumeUp && !isVolumeDown) return false

        val isHindi = _settings.value.language == AppLanguage.HINDI
        val strings = AppStrings.get(_settings.value.language)

        when (val screen = _currentScreen.value) {
            is Screen.Camera -> {
                // Shutter trigger on Camera screen
                capturePhoto(context, screen.task)
                return true
            }

            is Screen.ImagePreview -> {
                // Volume Up: Use photo; Volume Down: Retake
                if (isVolumeUp) {
                    speak(if (isHindi) "फोटो का उपयोग कर रहे हैं। विश्लेषण जारी है।" else "Using photo. Analyzing.")
                    navigateTo(Screen.Processing(screen.task, screen.imageBytes))
                } else {
                    speak(if (isHindi) "फोटो दोबारा खींच रहे हैं। कैमरा खुला।" else "Retaking photo. Camera opened.")
                    navigateTo(Screen.Camera(screen.task))
                }
                return true
            }

            is Screen.ReadTextResult -> {
                // Toggle speech playback
                if (_isSpeaking.value) {
                    stopSpeech()
                } else {
                    speak(screen.text)
                }
                return true
            }

            is Screen.DescriptionResult -> {
                // Toggle speech playback
                if (_isSpeaking.value) {
                    stopSpeech()
                } else {
                    speak(screen.description)
                }
                return true
            }

            is Screen.Welcome -> {
                speak(if (isHindi) "शुरू करें सक्रिय हुआ। ऑप्टीस्कैन होम में आपका स्वागत है।" else "Get Started activated. Welcome to OptiScan Home.")
                navigateTo(Screen.Home, clearBackstack = true)
                return true
            }

            is Screen.Home -> {
                speak(strings.homeSpokenAnnouncement)
                return true
            }

            else -> return false
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        val previousSettings = _settings.value
        val previousLanguage = previousSettings.language
        _settings.value = newSettings
        preferencesManager?.saveSettings(newSettings)
        repository.setMockMode(newSettings.useMockBackend)
        if (newSettings.customBackendUrl != previousSettings.customBackendUrl && newSettings.customBackendUrl.isNotBlank()) {
            repository.updateBaseUrl(newSettings.customBackendUrl)
        }
        applySettingsToTts(newSettings)

        // Announce language change to user with immediate accessibility feedback
        if (previousLanguage != newSettings.language) {
            val strings = AppStrings.get(newSettings.language)
            if (ttsManager?.isLanguageFallbackActive == true && newSettings.language == AppLanguage.HINDI) {
                speak(strings.hindiTtsFallbackNotice)
            } else {
                speak(strings.languageChangedAnnouncement)
            }
        }
    }

    private fun applySettingsToTts(appSettings: AppSettings) {
        ttsManager?.voiceFeedbackEnabled = appSettings.voiceFeedbackEnabled
        ttsManager?.setSpeechRate(appSettings.speechRate)
        ttsManager?.setLanguage(appSettings.language)
    }

    // Visual Analysis Pipeline
    fun analyzeCapturedImage(task: ScanTask, imageBytes: ByteArray? = null) {
        // Prevent duplicate concurrent requests
        if (_isAnalyzing.value) {
            Log.d(tag, "Analysis already in progress; ignoring duplicate request.")
            return
        }

        val lang = _settings.value.language
        val strings = AppStrings.get(lang)
        val bytesToAnalyze = imageBytes ?: _capturedBytes.value
        val isMock = repository.isMockMode()

        if (bytesToAnalyze == null && !isMock) {
            val err = if (lang == AppLanguage.HINDI) {
                "कोई फोटो डेटा नहीं मिला। कृपया पहले फोटो लें।"
            } else {
                "No captured image data found. Please capture a photo first."
            }
            _analysisError.value = err
            speak(err)
            return
        }

        _isAnalyzing.value = true
        _analysisError.value = null

        analysisJob = viewModelScope.launch {
            try {
                when (task) {
                    ScanTask.READ_TEXT -> {
                        speak(strings.processingReadingText)

                        // Step 1: Run on-device ML Kit OCR first if bitmap is available
                        var onDeviceText: String? = null
                        val bmp = _capturedBitmap.value
                        if (bmp != null) {
                            try {
                                val ocrRes = repository.readTextOnDevice(bmp)
                                if (ocrRes.isSuccess) {
                                    val recognized = ocrRes.getOrNull()?.trim()
                                    if (!recognized.isNullOrBlank() && recognized != "No printed text detected in the image.") {
                                        onDeviceText = recognized
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(tag, "On-device OCR check exception", e)
                            }
                        }

                        // Step 2: Query repository with image and optional on-device OCR text in selected language
                        val result = repository.analyzeImage(
                            imageBytes = bytesToAnalyze ?: ByteArray(0),
                            task = TaskTypes.READ_TEXT,
                            ocrText = onDeviceText,
                            language = lang.code
                        )

                        result.onSuccess { response ->
                            _isAnalyzing.value = false
                            if (response.shouldRecapture) {
                                val reason = response.reason ?: strings.recaptureBlurryReason
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = "",
                                        shouldRecapture = true,
                                        recaptureReason = reason,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.recaptureAnnouncement} $reason")
                            } else {
                                val finalAnswer = response.answer ?: onDeviceText ?: if (lang == AppLanguage.HINDI) {
                                    "छवि में कोई छपा हुआ टेक्स्ट नहीं मिला।"
                                } else {
                                    "No printed text detected in the image."
                                }
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = finalAnswer,
                                        shouldRecapture = false,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.textExtractedAnnouncement} $finalAnswer")
                            }
                        }.onFailure { ex ->
                            // Step 3: Graceful fallback to on-device OCR if cloud call failed
                            if (!onDeviceText.isNullOrBlank()) {
                                _isAnalyzing.value = false
                                navigateTo(
                                    Screen.ReadTextResult(
                                        text = onDeviceText,
                                        shouldRecapture = false,
                                        recaptureReason = strings.onDeviceFallbackNotice,
                                        isLiveResult = false
                                    )
                                )
                                speak("${strings.onDeviceFallbackSpoken} $onDeviceText")
                            } else {
                                _isAnalyzing.value = false
                                val errorMsg = ex.message ?: if (lang == AppLanguage.HINDI) {
                                    "छवि से टेक्स्ट पढ़ने में विफलता।"
                                } else {
                                    "Failed to read text from image."
                                }
                                _analysisError.value = errorMsg
                                speak("${strings.errorPrefix} $errorMsg. ${strings.retry}")
                            }
                        }
                    }

                    ScanTask.DESCRIBE_SURROUNDINGS -> {
                        speak(strings.processingDescribing)
                        val result = repository.analyzeImage(
                            imageBytes = bytesToAnalyze ?: ByteArray(0),
                            task = TaskTypes.DESCRIBE_IMAGE,
                            language = lang.code
                        )

                        result.onSuccess { response ->
                            _isAnalyzing.value = false
                            if (response.shouldRecapture) {
                                val reason = response.reason ?: strings.recaptureBlurryReason
                                navigateTo(
                                    Screen.DescriptionResult(
                                        description = "",
                                        shouldRecapture = true,
                                        recaptureReason = reason,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.recaptureAnnouncement} $reason")
                            } else {
                                val description = response.answer ?: if (lang == AppLanguage.HINDI) {
                                    "कोई विवरण उपलब्ध नहीं हो सका।"
                                } else {
                                    "No description could be generated."
                                }
                                navigateTo(
                                    Screen.DescriptionResult(
                                        description = description,
                                        shouldRecapture = false,
                                        isLiveResult = !isMock
                                    )
                                )
                                speak("${strings.sceneDescriptionAnnouncement} $description")
                            }
                        }.onFailure { ex ->
                            _isAnalyzing.value = false
                            val errorMsg = ex.message ?: if (lang == AppLanguage.HINDI) {
                                "आस-पास के वातावरण का विश्लेषण करने में विफलता।"
                            } else {
                                "Failed to analyze scene surroundings."
                            }
                            _analysisError.value = errorMsg
                            speak("${strings.errorPrefix} $errorMsg. ${strings.retry}")
                        }
                    }
                }
            } catch (e: CancellationException) {
                _isAnalyzing.value = false
                Log.i(tag, "Analysis task cancelled")
            } catch (e: Exception) {
                _isAnalyzing.value = false
                val errorMsg = e.message ?: if (lang == AppLanguage.HINDI) {
                    "एक अप्रत्याशित त्रुटि उत्पन्न हुई।"
                } else {
                    "An unexpected error occurred."
                }
                _analysisError.value = errorMsg
                speak("${strings.errorPrefix} $errorMsg")
            }
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        _isAnalyzing.value = false
        _analysisError.value = null
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.analysisCancelledSpoken)
    }

    fun askQuestion(
        query: String,
        contextContent: String?,
        onAnswerReceived: (String) -> Unit
    ) {
        if (query.isBlank()) return
        val bytes = _capturedBytes.value
        val isMock = repository.isMockMode()
        val lang = _settings.value.language
        val strings = AppStrings.get(lang)

        if (isMock) {
            val answer = MockData.answerQuestion(query, contextContent, lang.code)
            onAnswerReceived(answer)
            speak(answer)
            return
        }

        if (bytes == null) {
            val noImageMsg = strings.noImageForQuestionNotice
            onAnswerReceived(noImageMsg)
            speak(noImageMsg)
            return
        }

        viewModelScope.launch {
            speak(strings.findingAnswerSpoken)
            val result = repository.analyzeImage(
                imageBytes = bytes,
                task = TaskTypes.DESCRIBE_IMAGE,
                question = query,
                ocrText = contextContent,
                language = lang.code
            )
            result.onSuccess { response ->
                val answer = response.answer ?: if (lang == AppLanguage.HINDI) {
                    "इस प्रश्न के लिए कोई उत्तर नहीं मिल सका।"
                } else {
                    "No answer could be determined for this question."
                }
                onAnswerReceived(answer)
                speak(answer)
            }.onFailure { ex ->
                val errorMsg = if (lang == AppLanguage.HINDI) {
                    "AI सेवा से उत्तर प्राप्त करने में विफलता: ${ex.message ?: "नेटवर्क समस्या"}।"
                } else {
                    "Failed to get answer from AI service: ${ex.message ?: "Network error"}."
                }
                onAnswerReceived(errorMsg)
                speak(errorMsg)
            }
        }
    }

    // Spoken feedback methods
    fun speak(text: String, isScreenAnnouncement: Boolean = false) {
        // If TalkBack is active and this is just an auto screen title announcement, suppress raw TTS
        // so it doesn't talk over TalkBack's native heading announcement.
        if (isScreenAnnouncement && _isTalkBackActive.value) {
            return
        }

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
        val strings = AppStrings.get(_settings.value.language)
        speak(strings.testSpeechSentence)
    }

    fun testBackendConnection(url: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!url.isNullOrBlank()) {
                repository.updateBaseUrl(url)
            }
            val isHealthy = repository.checkBackendHealth()
            val strings = AppStrings.get(_settings.value.language)
            if (isHealthy) {
                speak(strings.connectionStatusConnected)
            } else {
                speak(strings.connectionStatusFailed)
            }
            onResult(isHealthy)
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        cameraManager.unbind()
        ttsManager?.shutdown()
    }
}
