package com.optiscan.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.optiscan.app.data.api.ApiClient
import com.optiscan.app.data.api.OptiScanApi
import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.StatusTypes
import com.optiscan.app.data.models.TaskTypes
import com.optiscan.app.ocr.OcrService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Repository interface defining visual analysis and OCR operations.
 */
interface ScanRepository {
    suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String? = null
    ): Result<AnalysisResponse>

    suspend fun readTextOnDevice(bitmap: Bitmap): Result<String>

    suspend fun checkBackendHealth(): Boolean

    fun setMockMode(enabled: Boolean)
}

/**
 * Concrete repository implementation with offline mock support and ML Kit integration.
 */
class ScanRepositoryImpl(
    private var api: OptiScanApi = ApiClient.createService(),
    private val ocrService: OcrService = OcrService(),
    private var useMockBackend: Boolean = false
) : ScanRepository {

    private val tag = "ScanRepository"

    override fun setMockMode(enabled: Boolean) {
        this.useMockBackend = enabled
        Log.i(tag, "Mock mode set to: $enabled")
    }

    override suspend fun checkBackendHealth(): Boolean = withContext(Dispatchers.IO) {
        if (useMockBackend) return@withContext true
        try {
            val response = api.checkHealth()
            response.status == "ok"
        } catch (e: Exception) {
            Log.w(tag, "Backend health check failed: ${e.message}")
            false
        }
    }

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String?
    ): Result<AnalysisResponse> = withContext(Dispatchers.IO) {
        if (useMockBackend) {
            Log.i(tag, "Returning mock backend response for task: $task")
            val mockAnswer = if (task == TaskTypes.DESCRIBE_IMAGE) {
                "A clear indoor hallway with doors on both sides. Straight ahead is unobstructed."
            } else {
                "ROOM 302 - ACCESSIBLE ENTRANCE"
            }
            return@withContext Result.success(
                AnalysisResponse(
                    status = StatusTypes.SUCCESS,
                    task = task,
                    answer = mockAnswer,
                    shouldRecapture = false,
                    reason = null
                )
            )
        }

        try {
            val imageRequestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", "camera_capture.jpg", imageRequestBody)
            val taskPart = task.toRequestBody("text/plain".toMediaTypeOrNull())
            val questionPart = question?.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.analyzeImage(imagePart, taskPart, questionPart)
            Result.success(response)
        } catch (e: Exception) {
            Log.e(tag, "Network request to backend failed", e)
            Result.failure(e)
        }
    }

    override suspend fun readTextOnDevice(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        ocrService.recognizeText(bitmap)
    }
}
