package com.optiscan.app.navigation

import com.optiscan.app.data.mock.MockData

/**
 * Scan task requested by the user.
 */
enum class ScanTask(val displayTitle: String, val subtitle: String) {
    READ_TEXT(
        displayTitle = "Read Text",
        subtitle = "Read labels, documents, medicine packaging, and signs."
    ),
    DESCRIBE_SURROUNDINGS(
        displayTitle = "Describe Surroundings",
        subtitle = "Describe objects, rooms, and walking paths."
    )
}

/**
 * Target screens for OptiScan navigation.
 */
sealed interface Screen {
    data object Welcome : Screen
    data object Home : Screen
    data class Camera(val task: ScanTask) : Screen
    data class ImagePreview(val task: ScanTask, val imageBytes: ByteArray? = null) : Screen
    data class Processing(val task: ScanTask, val imageBytes: ByteArray? = null) : Screen
    data class ReadTextResult(
        val text: String = MockData.SAMPLE_PRODUCT_LABEL,
        val shouldRecapture: Boolean = false,
        val recaptureReason: String? = null,
        val isLiveResult: Boolean = true
    ) : Screen
    data class DescriptionResult(
        val description: String = MockData.SAMPLE_SCENE_DESCRIPTION,
        val shouldRecapture: Boolean = false,
        val recaptureReason: String? = null,
        val isLiveResult: Boolean = true
    ) : Screen
    data class AskQuestion(
        val contextTitle: String? = null,
        val contextContent: String? = null
    ) : Screen
    data object Settings : Screen
}

/**
 * Supported application languages.
 */
enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिंदी");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

/**
 * Global application accessibility preferences.
 */
data class AppSettings(
    val speechRate: Float = 1.0f,
    val voiceFeedbackEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val textSizeScale: Float = 1.0f,
    val useMockBackend: Boolean = false,
    val tripleTapNavigationEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val customBackendUrl: String = ""
) {
    val textSizeLabel: String
        get() = when {
            textSizeScale >= 1.35f -> "Extra Large"
            textSizeScale >= 1.15f -> "Large"
            else -> "Default"
        }
}

