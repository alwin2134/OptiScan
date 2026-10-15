package com.optiscan.app.ondevice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Resilient DNS resolver for OkHttp with automatic fallback to DNS-over-HTTPS (DoH).
 * Resolves hostnames like "us.aws.cdn.hf.co" via direct-IP Cloudflare / Google DoH
 * if mobile carrier DNS (e.g. Jio / Airtel) fails or blocks CDN subdomains.
 */
class ResilientDns : Dns {
    private val tag = "ResilientDns"

    override fun lookup(hostname: String): List<InetAddress> {
        // 1. Try standard system DNS first
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (e: Exception) {
            Log.w(tag, "System DNS lookup failed for $hostname: ${e.message}. Trying DoH fallback.")
        }

        // 2. Query Cloudflare DoH via direct IP 1.1.1.1 (no DNS needed to reach the resolver)
        try {
            val cfUrl = URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
            val conn = cfUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("Accept", "application/dns-json")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val ips = extractIpsFromJson(json)
                if (ips.isNotEmpty()) {
                    Log.i(tag, "Resolved $hostname via Cloudflare DoH: $ips")
                    return ips.map { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(tag, "Cloudflare DoH fallback failed for $hostname: ${e.message}")
        }

        // 3. Query Google DoH (8.8.8.8)
        try {
            val googleUrl = URL("https://dns.google/resolve?name=$hostname&type=A")
            val conn = googleUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val ips = extractIpsFromJson(json)
                if (ips.isNotEmpty()) {
                    Log.i(tag, "Resolved $hostname via Google DoH: $ips")
                    return ips.map { InetAddress.getByAddress(hostname, InetAddress.getByName(it).address) }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(tag, "Google DoH fallback failed for $hostname: ${e.message}")
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\" via system DNS and DoH fallbacks")
    }

    private fun extractIpsFromJson(json: String): List<String> {
        val list = mutableListOf<String>()
        val regex = Regex(""""data"\s*:\s*"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})"""")
        regex.findAll(json).forEach { match ->
            list.add(match.groupValues[1])
        }
        return list
    }
}

/**
 * Sealed state for the on-device Qwen VL model download and readiness lifecycle.
 */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progressPercent: Int, val downloadedMb: Float, val totalMb: Float) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
    data object Cancelled : ModelDownloadState
}

/**
 * Manages the on-device Qwen2-VL GGUF model file lifecycle:
 * download, storage checks, verification, deletion, and state reporting.
 *
 * Model: Qwen2-VL-2B-Instruct-Q4_K_M.gguf (~1.5 GB)
 * This is a quantized multimodal model that supports both image and text input
 * via llama.cpp's multimodal projector system.
 *
 * Privacy guarantee: model weights and all inference run strictly on-device.
 * No image data or conversation is uploaded anywhere.
 */
class OnDeviceModelManager(private val context: Context) {

    private val tag = "OnDeviceModelManager"

    companion object {
        /**
         * Primary: Qwen2-VL-2B-Instruct Q4_K_M GGUF (~1.5 GB)
         * This is the smallest Qwen2-VL variant that supports multimodal (vision+text) input.
         * It requires llama.cpp multimodal support (clip projector).
         */
        const val MODEL_FILENAME = "qwen2-vl-2b-instruct-q4_k_m.gguf"
        const val MMPROJ_FILENAME = "qwen2-vl-2b-instruct-mmproj-f16.gguf"

        /**
         * Verified public Hugging Face download mirrors for Qwen2-VL GGUF.
         * Primary: ggml-org (official GGUF organization)
         * Fallback: bartowski (well-maintained community quantization)
         */
        val MODEL_URLS = listOf(
            "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
            "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf"
        )

        val MMPROJ_URLS = listOf(
            "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f16.gguf",
            "https://huggingface.co/bartowski/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f16.gguf"
        )

        const val MODEL_URL = "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/Qwen2-VL-2B-Instruct-Q4_K_M.gguf"
        const val MMPROJ_URL = "https://huggingface.co/ggml-org/Qwen2-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen2-VL-2B-Instruct-f16.gguf"

        /** Minimum free storage in bytes required before starting download (~4 GB headroom) */
        const val MIN_FREE_BYTES: Long = 4L * 1024 * 1024 * 1024

        /** Expected approximate model sizes for progress display */
        const val MODEL_APPROX_BYTES: Long = 1_600_000_000L   // ~1.5 GB
        const val MMPROJ_APPROX_BYTES: Long = 300_000_000L     // ~300 MB

        /** Minimum RAM recommended for inference (3 GB) */
        const val MIN_RECOMMENDED_RAM_MB: Long = 3072L
    }

    private val httpClient = OkHttpClient.Builder()
        .dns(ResilientDns())
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    /** Internal cancellation token for download */
    private var downloadJob: Job? = null

    /** Directory where model files are stored */
    val modelsDir: File
        get() {
            val extDir = context.getExternalFilesDir("models")
            if (extDir != null && File(extDir, MODEL_FILENAME).exists() && File(extDir, MMPROJ_FILENAME).exists()) {
                return extDir
            }
            return File(context.filesDir, "ondevice_models").also { it.mkdirs() }
        }

    val modelFile: File
        get() {
            val extDir = context.getExternalFilesDir("models")
            if (extDir != null && File(extDir, MODEL_FILENAME).exists()) {
                return File(extDir, MODEL_FILENAME)
            }
            return File(File(context.filesDir, "ondevice_models").also { it.mkdirs() }, MODEL_FILENAME)
        }

    val mmprojFile: File
        get() {
            val extDir = context.getExternalFilesDir("models")
            if (extDir != null && File(extDir, MMPROJ_FILENAME).exists()) {
                return File(extDir, MMPROJ_FILENAME)
            }
            return File(File(context.filesDir, "ondevice_models").also { it.mkdirs() }, MMPROJ_FILENAME)
        }

    /** True when both model and mmproj files exist with non-zero size in either internal or external storage. */
    fun isModelReady(): Boolean {
        val internalModel = File(File(context.filesDir, "ondevice_models"), MODEL_FILENAME)
        val internalMmproj = File(File(context.filesDir, "ondevice_models"), MMPROJ_FILENAME)
        if (internalModel.exists() && internalModel.length() > 0L &&
            internalMmproj.exists() && internalMmproj.length() > 0L) {
            return true
        }

        val extDir = context.getExternalFilesDir("models")
        if (extDir != null) {
            val extModel = File(extDir, MODEL_FILENAME)
            val extMmproj = File(extDir, MMPROJ_FILENAME)
            if (extModel.exists() && extModel.length() > 0L &&
                extMmproj.exists() && extMmproj.length() > 0L) {
                return true
            }
        }
        return false
    }

    /** Returns the current state as a snapshot */
    fun currentState(): ModelDownloadState = _downloadState.value

    /**
     * Check available free storage space on the device's internal storage.
     */
    fun availableStorageBytes(): Long {
        return context.filesDir.freeSpace
    }

    /**
     * Retrieves total device RAM in MB for device capability warnings.
     */
    fun totalRamMb(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        return memInfo.totalMem / (1024 * 1024)
    }

    /**
     * Returns a human-readable storage requirement message.
     */
    fun storageRequirementMessage(): String {
        val freeMb = availableStorageBytes() / (1024 * 1024)
        val requiredMb = (MODEL_APPROX_BYTES + MMPROJ_APPROX_BYTES) / (1024 * 1024)
        return "Required: ~${requiredMb} MB | Available: ${freeMb} MB"
    }

    /**
     * Download both the main GGUF model and the multimodal projector.
     * Emits [ModelDownloadState] progress. This is a suspending function — run in a coroutine scope.
     * Cancellation is cooperative: cancel the parent coroutine Job.
     */
    suspend fun downloadModel(
        onProgress: (ModelDownloadState) -> Unit = {}
    ) {
        _downloadState.value = ModelDownloadState.Downloading(0, 0f, 0f)
        onProgress(_downloadState.value)

        try {
            // Storage check
            val freeBytes = availableStorageBytes()
            if (freeBytes < MIN_FREE_BYTES) {
                val msg = "Insufficient storage. Need ~${MIN_FREE_BYTES / (1024 * 1024)} MB free. " +
                          "Available: ${freeBytes / (1024 * 1024)} MB. Please free up space and try again."
                _downloadState.value = ModelDownloadState.Failed(msg)
                onProgress(_downloadState.value)
                return
            }

            val totalBytes = MODEL_APPROX_BYTES + MMPROJ_APPROX_BYTES
            var downloadedBytes = 0L

            // Download main model (larger file first)
            if (!modelFile.exists() || modelFile.length() < MODEL_APPROX_BYTES / 2) {
                Log.i(tag, "Downloading main model GGUF...")
                downloadedBytes += downloadFile(
                    urls = MODEL_URLS,
                    targetFile = modelFile,
                    fileLabel = "model",
                    alreadyDownloaded = downloadedBytes,
                    onProgress = { dlBytes ->
                        downloadedBytes = dlBytes
                        val pct = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                        val dlMb = downloadedBytes / (1024f * 1024f)
                        val totalMb = totalBytes / (1024f * 1024f)
                        val state = ModelDownloadState.Downloading(pct, dlMb, totalMb)
                        _downloadState.value = state
                        onProgress(state)
                    }
                )
            } else {
                downloadedBytes += modelFile.length()
                Log.i(tag, "Main model file already exists (${modelFile.length()} bytes), skipping.")
            }

            // Download mmproj (multimodal projector - required for vision)
            if (!mmprojFile.exists() || mmprojFile.length() < MMPROJ_APPROX_BYTES / 2) {
                Log.i(tag, "Downloading multimodal projector GGUF...")
                downloadedBytes += downloadFile(
                    urls = MMPROJ_URLS,
                    targetFile = mmprojFile,
                    fileLabel = "mmproj",
                    alreadyDownloaded = downloadedBytes,
                    onProgress = { dlBytes ->
                        downloadedBytes = dlBytes
                        val pct = ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                        val dlMb = downloadedBytes / (1024f * 1024f)
                        val totalMb = totalBytes / (1024f * 1024f)
                        val state = ModelDownloadState.Downloading(pct, dlMb, totalMb)
                        _downloadState.value = state
                        onProgress(state)
                    }
                )
            } else {
                Log.i(tag, "Mmproj file already exists (${mmprojFile.length()} bytes), skipping.")
            }

            // Verify both files
            _downloadState.value = ModelDownloadState.Verifying
            onProgress(_downloadState.value)

            if (isModelReady()) {
                Log.i(tag, "Model download complete and verified.")
                _downloadState.value = ModelDownloadState.Ready
                onProgress(_downloadState.value)
            } else {
                val missingFiles = buildString {
                    if (!modelFile.exists() || modelFile.length() == 0L) append("model GGUF, ")
                    if (!mmprojFile.exists() || mmprojFile.length() == 0L) append("mmproj GGUF")
                }
                _downloadState.value = ModelDownloadState.Failed("Download verification failed: missing $missingFiles. Please retry.")
                onProgress(_downloadState.value)
            }

        } catch (e: CancellationException) {
            Log.i(tag, "Download cancelled by user")
            _downloadState.value = ModelDownloadState.Cancelled
            onProgress(_downloadState.value)
            // Clean up partial files on cancellation
            cleanPartialFiles()
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Download failed", e)
            val msg = when {
                e.message?.contains("UnknownHost") == true ||
                e.message?.contains("failed to connect") == true ->
                    "Network error: Cannot reach download server. Check your internet connection and try again."
                e.message?.contains("No space left") == true ->
                    "Device storage full. Please free up space and try again."
                else -> "Download failed: ${e.message ?: "Unknown error"}. Please try again."
            }
            _downloadState.value = ModelDownloadState.Failed(msg)
            onProgress(_downloadState.value)
            cleanPartialFiles()
        }
    }

    /**
     * Download a single file with streaming progress updates across mirror URLs.
     * Uses OkHttp with automatic redirect and SSL support.
     * Returns the number of bytes downloaded in this call.
     */
    private suspend fun downloadFile(
        urls: List<String>,
        targetFile: File,
        fileLabel: String,
        alreadyDownloaded: Long,
        onProgress: (Long) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        var lastException: Exception? = null

        for (urlStr in urls) {
            var bytesThisFile = 0L
            try {
                Log.i(tag, "Attempting to download $fileLabel from $urlStr")
                val request = Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    throw IOException("HTTP $code for $fileLabel download from $urlStr")
                }

                val body = response.body ?: throw IOException("Empty response body for $fileLabel")
                body.byteStream().use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(256 * 1024) // 256 KB buffer
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Download cancelled")
                            outputStream.write(buffer, 0, bytesRead)
                            bytesThisFile += bytesRead
                            onProgress(alreadyDownloaded + bytesThisFile)
                        }
                    }
                }
                response.close()

                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)

                Log.i(tag, "$fileLabel downloaded successfully: ${bytesThisFile / (1024 * 1024)} MB")
                return@withContext bytesThisFile
            } catch (e: CancellationException) {
                if (tempFile.exists()) tempFile.delete()
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Failed downloading $fileLabel from $urlStr: ${e.message}, trying next mirror")
                if (tempFile.exists()) tempFile.delete()
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to download $fileLabel from all available mirrors")
    }

    /**
     * Mark model as ready if files already exist (called at app startup).
     */
    fun updateStateFromDisk() {
        _downloadState.value = if (isModelReady()) {
            ModelDownloadState.Ready
        } else {
            ModelDownloadState.Idle
        }
    }

    /**
     * Delete all model files to free storage.
     */
    fun deleteModelFiles() {
        modelFile.takeIf { it.exists() }?.delete()
        mmprojFile.takeIf { it.exists() }?.delete()
        File(modelsDir, "${MODEL_FILENAME}.tmp").takeIf { it.exists() }?.delete()
        File(modelsDir, "${MMPROJ_FILENAME}.tmp").takeIf { it.exists() }?.delete()
        _downloadState.value = ModelDownloadState.Idle
        Log.i(tag, "Model files deleted.")
    }

    private fun cleanPartialFiles() {
        File(modelsDir, "${MODEL_FILENAME}.tmp").takeIf { it.exists() }?.delete()
        File(modelsDir, "${MMPROJ_FILENAME}.tmp").takeIf { it.exists() }?.delete()
    }
}
