package com.optiscan.app.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
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
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Repository interface defining visual analysis and OCR operations.
 */
interface ScanRepository {
    suspend fun analyzeImage(
        imageBytes: ByteArray,
        task: String,
        question: String? = null,
        ocrText: String? = null,
        language: String = "en"
    ): Result<AnalysisResponse>

    suspend fun readTextOnDevice(bitmap: Bitmap): Result<String>

    suspend fun checkBackendHealth(): Boolean

    fun setMockMode(enabled: Boolean)

    fun isMockMode(): Boolean
}

/**
 * Concrete repository implementation with offline mock support and ML Kit integration.
 */
class ScanRepositoryImpl(
    private var api: OptiScanApi = ApiClient.createService(),
    ocrService: OcrService? = null,
    private var useMockBackend: Boolean = false
) : ScanRepository {

    private val ocrService: OcrService by lazy { ocrService ?: OcrService() }

    private val tag = "ScanRepository"
    private val gson = Gson()

    override fun setMockMode(enabled: Boolean) {
        this.useMockBackend = enabled
        Log.i(tag, "Mock mode set to: $enabled")
    }

    override fun isMockMode(): Boolean = useMockBackend

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
        question: String?,
        ocrText: String?,
        language: String
    ): Result<AnalysisResponse> = withContext(Dispatchers.IO) {
        if (useMockBackend) {
            Log.i(tag, "Returning mock backend response for task: $task, language: $language")
            val isHindi = language.equals("hi", ignoreCase = true)
            val mockAnswer = if (task == TaskTypes.DESCRIBE_IMAGE) {
                if (isHindi) {
                    "[ऑफलाइन मॉक] दोनों तरफ दरवाजों वाला एक सीधा और साफ इनडोर गलियारा। आगे का रास्ता पूरी तरह खुला और सुरक्षित है।"
                } else {
                    "[Offline Mock] A clear indoor hallway with doors on both sides. Straight ahead is unobstructed."
                }
            } else {
                if (isHindi) {
                    "[ऑफलाइन मॉक] ROOM 302 - ACCESSIBLE ENTRANCE"
                } else {
                    "[Offline Mock] ROOM 302 - ACCESSIBLE ENTRANCE"
                }
            }
            return@withContext Result.success(
                AnalysisResponse(
                    status = StatusTypes.SUCCESS,
                    task = task,
                    answer = mockAnswer,
                    shouldRecapture = false,
                    reason = if (isHindi) "ऑफलाइन मॉक बैकएंड मोड सक्षम है।" else "Offline mock backend mode is enabled."
                )
            )
        }

        try {
            val imageRequestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", "camera_capture.jpg", imageRequestBody)
            val taskPart = task.toRequestBody("text/plain".toMediaTypeOrNull())
            val questionPart = question?.toRequestBody("text/plain".toMediaTypeOrNull())
            val ocrPart = ocrText?.toRequestBody("text/plain".toMediaTypeOrNull())
            val langPart = language.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.analyzeImage(imagePart, taskPart, questionPart, ocrPart, langPart)
            Result.success(response)
        } catch (e: HttpException) {
            Log.e(tag, "HTTP error from backend: ${e.code()}", e)
            val errorBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsedResponse = try {
                if (!errorBody.isNullOrBlank()) {
                    gson.fromJson(errorBody, AnalysisResponse::class.java)
                } else null
            } catch (_: Exception) {
                null
            }

            if (parsedResponse != null) {
                if (parsedResponse.status == StatusTypes.RECAPTURE || parsedResponse.shouldRecapture) {
                    Result.success(parsedResponse)
                } else {
                    val message = parsedResponse.reason ?: "Server error (${e.code()})"
                    Result.failure(Exception(message, e))
                }
            } else {
                val message = when (e.code()) {
                    400 -> "Bad request. The captured image or task parameters were invalid."
                    502 -> "Vision AI provider unavailable. Please try again."
                    500 -> "Backend server error. Please try again later."
                    else -> "Server error (${e.code()}). Please try again."
                }
                Result.failure(Exception(message, e))
            }
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Connection timed out", e)
            Result.failure(Exception("Connection to AI backend timed out. Please check your network and try again.", e))
        } catch (e: IOException) {
            Log.e(tag, "Network I/O failure", e)
            Result.failure(Exception("Could not connect to OptiScan backend. Please verify your connection or enable Offline Mock in Settings.", e))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in analyzeImage", e)
            Result.failure(e)
        }
    }

    override suspend fun readTextOnDevice(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        ocrService.recognizeText(bitmap)
    }
}
