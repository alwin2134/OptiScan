package com.optiscan.app.ondevice

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.app.navigation.AppLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing on-device Qwen2-VL voice conversation state.
 *
 * Responsibilities:
 * - Model download lifecycle (with progress/cancellation)
 * - Model load/unload from memory
 * - Multi-turn conversation context (image + chat history)
 * - Non-blocking inference on viewModelScope (IO dispatcher)
 * - Diagnostic benchmarks (load time, inference time)
 * - Resource cleanup on cleared()
 *
 * Architecture:
 * This ViewModel owns the OnDeviceModelManager and OnDeviceInferenceEngine.
 * It does NOT own TTS or SpeechInput — those are coordinated by MainViewModel.
 * The UI layer calls generateAnswer() and receives results via onAnswerReady callback.
 */
class OnDeviceConversationViewModel : ViewModel() {

    private val tag = "OnDeviceConvVM"

    // Model manager and inference engine (initialized lazily with context)
    private var modelManager: OnDeviceModelManager? = null
    private var inferenceEngine: OnDeviceInferenceEngine? = null

    // ---- Download State ----
    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    // ---- Inference State ----
    private val _inferenceState = MutableStateFlow<InferenceState>(InferenceState.Unloaded)
    val inferenceState: StateFlow<InferenceState> = _inferenceState.asStateFlow()

    // ---- Conversation History ----
    private val _conversationHistory = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationTurn>> = _conversationHistory.asStateFlow()

    // ---- Current image context ----
    private val _currentImageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentImageBitmap: StateFlow<Bitmap?> = _currentImageBitmap.asStateFlow()

    // ---- Error state ----
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- Diagnostics ----
    private val _diagnosticInfo = MutableStateFlow<DiagnosticInfo?>(null)
    val diagnosticInfo: StateFlow<DiagnosticInfo?> = _diagnosticInfo.asStateFlow()

    // ---- Storage / Device info ----
    private val _deviceInfo = MutableStateFlow<DeviceCapabilityInfo?>(null)
    val deviceInfo: StateFlow<DeviceCapabilityInfo?> = _deviceInfo.asStateFlow()

    private var downloadJob: Job? = null
    private var inferenceJob: Job? = null

    /**
     * Initialize with application context. Call once from the UI layer (Activity/Composable).
     */
    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        if (modelManager == null) {
            modelManager = OnDeviceModelManager(appCtx)
            inferenceEngine = OnDeviceInferenceEngine(appCtx)
        }

        // Sync download state from disk
        modelManager?.updateStateFromDisk()
        val diskState = modelManager?.currentState() ?: ModelDownloadState.Idle
        _downloadState.value = diskState

        // Collect engine inference state
        viewModelScope.launch {
            inferenceEngine?.inferenceState?.collect { state ->
                _inferenceState.value = state
            }
        }

        // Assess device capabilities
        assessDeviceCapabilities(appCtx)

        // Auto-load model if already downloaded
        if (diskState == ModelDownloadState.Ready) {
            loadModelIntoMemory()
        }

        Log.i(tag, "OnDeviceConversationViewModel initialized. Model ready: ${modelManager?.isModelReady()}")
    }

    /**
     * Start downloading the Qwen2-VL GGUF model files.
     * Download runs in the background; progress is observable via [downloadState].
     */
    fun startDownload() {
        val manager = modelManager ?: return
        _errorMessage.value = null

        downloadJob = viewModelScope.launch {
            try {
                manager.downloadModel { state ->
                    _downloadState.value = state
                }
                // Auto-load once download completes
                if (_downloadState.value == ModelDownloadState.Ready) {
                    loadModelIntoMemory()
                }
            } catch (e: CancellationException) {
                Log.i(tag, "Download cancelled")
                _downloadState.value = ModelDownloadState.Cancelled
            }
        }
    }

    /**
     * Cancel an in-progress download.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /**
     * Load the GGUF model weights into device RAM for inference.
     * This is expensive (~5-15s) and runs off the UI thread.
     */
    fun loadModelIntoMemory() {
        val manager = modelManager ?: return
        val engine = inferenceEngine ?: return
        if (!manager.isModelReady()) return
        if (engine.isLoaded()) return  // Already loaded

        viewModelScope.launch {
            val startMs = System.currentTimeMillis()
            val result = engine.loadModel(manager.modelFile, manager.mmprojFile)
            val loadMs = System.currentTimeMillis() - startMs

            result.onSuccess {
                updateDiagnostics(modelLoadTimeMs = loadMs)
                Log.i(tag, "Model loaded into memory in ${loadMs}ms")
            }.onFailure { ex ->
                _errorMessage.value = ex.message
                Log.e(tag, "Failed to load model", ex)
            }
        }
    }

    /**
     * Unload model from RAM to free memory.
     */
    fun unloadModel() {
        inferenceEngine?.release()
    }

    /**
     * Set the current image context for the conversation.
     * This should be called whenever the user captures a new photo.
     * Setting a new image clears the conversation history.
     */
    fun setCurrentImage(bitmap: Bitmap?) {
        _currentImageBitmap.value = bitmap
        _conversationHistory.value = emptyList()  // Clear history for new image context
        _errorMessage.value = null
        Log.i(tag, "New image set, conversation history cleared")
    }

    /**
     * Generate an on-device AI response for [userQuery].
     * Uses the current image context (if any) and full conversation history.
     * The result is delivered via [onAnswerReady] callback.
     * Inference runs off the UI thread.
     */
    fun generateAnswer(
        userQuery: String,
        language: AppLanguage,
        onAnswerReady: (Result<String>) -> Unit
    ) {
        val engine = inferenceEngine ?: run {
            onAnswerReady(Result.failure(Exception("Inference engine not initialized.")))
            return
        }

        if (!engine.isLoaded()) {
            onAnswerReady(Result.failure(Exception("Model not loaded. Please wait for it to finish loading.")))
            return
        }

        if (userQuery.isBlank()) return

        // Cancel any ongoing inference
        inferenceJob?.cancel()
        _errorMessage.value = null

        inferenceJob = viewModelScope.launch {
            // Add user turn to history immediately
            val userTurn = ConversationTurn(text = userQuery, isUser = true)
            _conversationHistory.value = _conversationHistory.value + userTurn

            val startMs = System.currentTimeMillis()

            val result = engine.generateResponse(
                userQuery = userQuery,
                imageBitmap = _currentImageBitmap.value,
                conversationHistory = _conversationHistory.value.dropLast(1), // exclude current turn
                language = language.code,
                onPartialToken = { partial ->
                    // Partial streaming — update last assistant turn in real time
                    // (optional: update UI with streaming text)
                }
            )

            val inferMs = System.currentTimeMillis() - startMs
            updateDiagnostics(lastInferenceMs = inferMs)

            result.onSuccess { answer ->
                // Add assistant turn to history
                val assistantTurn = ConversationTurn(text = answer, isUser = false)
                _conversationHistory.value = _conversationHistory.value + assistantTurn
                Log.i(tag, "Generated answer (${inferMs}ms, ${answer.length} chars)")
            }.onFailure { ex ->
                // Remove failed user turn from history
                _conversationHistory.value = _conversationHistory.value.dropLast(1)
                _errorMessage.value = ex.message
                Log.e(tag, "Inference failed", ex)
            }

            onAnswerReady(result)
        }
    }

    /**
     * Cancel any ongoing inference.
     */
    fun cancelInference() {
        inferenceJob?.cancel()
        inferenceJob = null
    }

    /**
     * Clear conversation history (start fresh with same image).
     */
    fun clearConversation() {
        _conversationHistory.value = emptyList()
        _errorMessage.value = null
    }

    /**
     * Delete model files from storage to free space.
     */
    fun deleteModel() {
        unloadModel()
        modelManager?.deleteModelFiles()
        _downloadState.value = ModelDownloadState.Idle
        _conversationHistory.value = emptyList()
        _currentImageBitmap.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Returns storage requirement info string for display.
     */
    fun storageInfo(): String = modelManager?.storageRequirementMessage() ?: "Storage info unavailable"

    /**
     * Returns formatted diagnostic/benchmark data for developer display.
     */
    fun formattedDiagnostics(): String {
        val info = _diagnosticInfo.value ?: return "No diagnostics yet"
        val device = _deviceInfo.value
        return buildString {
            append("🔧 Diagnostics\n")
            append("• Model: Qwen2-VL-2B Q4_K_M (stub=${OnDeviceInferenceEngine.USE_STUB})\n")
            append("• Load time: ${info.modelLoadTimeMs}ms\n")
            append("• Last inference: ${info.lastInferenceMs}ms\n")
            append("• Turns: ${_conversationHistory.value.size}\n")
            if (device != null) {
                append("• RAM: ${device.totalRamMb} MB (${if (device.meetsMinimumRam) "✓" else "⚠ low"})\n")
                append("• Storage: ${device.availableStorageMb} MB free\n")
                append("• ABI: ${device.abi}\n")
            }
        }
    }

    private fun updateDiagnostics(modelLoadTimeMs: Long? = null, lastInferenceMs: Long? = null) {
        val current = _diagnosticInfo.value ?: DiagnosticInfo()
        _diagnosticInfo.value = current.copy(
            modelLoadTimeMs = modelLoadTimeMs ?: current.modelLoadTimeMs,
            lastInferenceMs = lastInferenceMs ?: current.lastInferenceMs
        )
    }

    private fun assessDeviceCapabilities(context: Context) {
        val manager = OnDeviceModelManager(context)
        val ramMb = manager.totalRamMb()
        val freeMb = manager.availableStorageBytes() / (1024 * 1024)
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        _deviceInfo.value = DeviceCapabilityInfo(
            totalRamMb = ramMb,
            availableStorageMb = freeMb,
            abi = abi,
            meetsMinimumRam = ramMb >= OnDeviceModelManager.MIN_RECOMMENDED_RAM_MB,
            hasArm64 = abi.startsWith("arm64")
        )
    }

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        inferenceJob?.cancel()
        inferenceEngine?.release()
        Log.i(tag, "OnDeviceConversationViewModel cleared")
    }
}

data class DiagnosticInfo(
    val modelLoadTimeMs: Long = 0L,
    val lastInferenceMs: Long = 0L
)

data class DeviceCapabilityInfo(
    val totalRamMb: Long,
    val availableStorageMb: Long,
    val abi: String,
    val meetsMinimumRam: Boolean,
    val hasArm64: Boolean
)
