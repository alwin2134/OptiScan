package com.optiscan.app.data.api

import com.optiscan.app.data.models.AnalysisResponse
import com.optiscan.app.data.models.HealthResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit service definition for OptiScan backend endpoints.
 */
interface OptiScanApi {

    /**
     * Health check endpoint to verify backend reachability.
     */
    @GET("health")
    suspend fun checkHealth(): HealthResponse

    /**
     * Analyze uploaded image for scene description or text reading.
     *
     * @param image Multipart image payload (JPEG, PNG, WEBP).
     * @param task Request body with "describe_image" or "read_text".
     * @param question Optional question about the image context.
     */
    @Multipart
    @POST("analyze")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part,
        @Part("task") task: RequestBody,
        @Part("question") question: RequestBody? = null,
        @Part("ocr_text") ocrText: RequestBody? = null
    ): AnalysisResponse
}
