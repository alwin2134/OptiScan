package com.optiscan.app.navigation

/**
 * UI State representation for the OptiScan workflow.
 */
sealed interface ScreenState {
    /**
     * Camera is live and ready for user action.
     */
    data object Idle : ScreenState

    /**
     * Capturing photo from CameraX.
     */
    data object Capturing : ScreenState

    /**
     * Processing image via on-device ML Kit or backend vision AI.
     */
    data class Processing(val taskName: String) : ScreenState

    /**
     * Analysis completed with descriptive text, OCR readout, or recapture instruction.
     */
    data class Result(
        val taskName: String,
        val text: String,
        val shouldRecapture: Boolean = false,
        val recaptureReason: String? = null
    ) : ScreenState

    /**
     * Error state encountered during capture, processing, or network call.
     */
    data class Error(val errorMessage: String) : ScreenState

    /**
     * Camera permission not granted.
     */
    data object PermissionDenied : ScreenState
}
