package com.optiscan.app.data.models

import com.google.gson.annotations.SerializedName

/**
 * Standard response payload returned by the OptiScan /analyze endpoint.
 *
 * Matches the backend schema in app.schemas.analysis.AnalysisResponse.
 */
data class AnalysisResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("task")
    val task: String,

    @SerializedName("answer")
    val answer: String? = null,

    @SerializedName("should_recapture")
    val shouldRecapture: Boolean = false,

    @SerializedName("reason")
    val reason: String? = null
)

/**
 * Health check response payload from GET /health.
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("service")
    val service: String
)

/**
 * Supported task types for image processing requests.
 */
object TaskTypes {
    const val DESCRIBE_IMAGE = "describe_image"
    const val READ_TEXT = "read_text"
}

/**
 * Response status values.
 */
object StatusTypes {
    const val SUCCESS = "success"
    const val ERROR = "error"
    const val RECAPTURE = "recapture"
}
