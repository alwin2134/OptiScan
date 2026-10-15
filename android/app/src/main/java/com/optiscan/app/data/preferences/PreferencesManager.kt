package com.optiscan.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings

/**
 * Manages persistence of OptiScan settings and language selection using SharedPreferences.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Load persisted application settings, with accessible defaults.
     */
    fun loadSettings(): AppSettings {
        val langCode = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code)
        val speechRate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        val voiceFeedback = prefs.getBoolean(KEY_VOICE_FEEDBACK, true)
        val hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        val textSizeScale = prefs.getFloat(KEY_TEXT_SIZE_SCALE, 1.0f)
        val useMockBackend = prefs.getBoolean(KEY_USE_MOCK_BACKEND, false)
        val tripleTapNavigation = prefs.getBoolean(KEY_TRIPLE_TAP_NAVIGATION, true)
        val useOnDeviceAi = prefs.getBoolean(KEY_USE_ON_DEVICE_AI, false)
        val showDiagnostics = prefs.getBoolean(KEY_SHOW_ON_DEVICE_DIAGNOSTICS, false)

        return AppSettings(
            speechRate = speechRate,
            voiceFeedbackEnabled = voiceFeedback,
            hapticFeedbackEnabled = hapticFeedback,
            textSizeScale = textSizeScale,
            useMockBackend = useMockBackend,
            tripleTapNavigationEnabled = tripleTapNavigation,
            language = AppLanguage.fromCode(langCode),
            useOnDeviceAi = useOnDeviceAi,
            showOnDeviceDiagnostics = showDiagnostics
        )
    }

    /**
     * Persist application settings.
     */
    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_LANGUAGE, settings.language.code)
            .putFloat(KEY_SPEECH_RATE, settings.speechRate)
            .putBoolean(KEY_VOICE_FEEDBACK, settings.voiceFeedbackEnabled)
            .putBoolean(KEY_HAPTIC_FEEDBACK, settings.hapticFeedbackEnabled)
            .putFloat(KEY_TEXT_SIZE_SCALE, settings.textSizeScale)
            .putBoolean(KEY_USE_MOCK_BACKEND, settings.useMockBackend)
            .putBoolean(KEY_TRIPLE_TAP_NAVIGATION, settings.tripleTapNavigationEnabled)
            .putBoolean(KEY_USE_ON_DEVICE_AI, settings.useOnDeviceAi)
            .putBoolean(KEY_SHOW_ON_DEVICE_DIAGNOSTICS, settings.showOnDeviceDiagnostics)
            .apply()
    }

    /**
     * Quick setter for selected language.
     */
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
    }

    /**
     * Retrieve current language preference.
     */
    fun getLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code)
        return AppLanguage.fromCode(code)
    }

    companion object {
        const val PREFS_NAME = "optiscan_preferences"
        const val KEY_LANGUAGE = "key_app_language"
        const val KEY_SPEECH_RATE = "key_speech_rate"
        const val KEY_VOICE_FEEDBACK = "key_voice_feedback"
        const val KEY_HAPTIC_FEEDBACK = "key_haptic_feedback"
        const val KEY_TEXT_SIZE_SCALE = "key_text_size_scale"
        const val KEY_USE_MOCK_BACKEND = "key_use_mock_backend"
        const val KEY_TRIPLE_TAP_NAVIGATION = "key_triple_tap_navigation"
        const val KEY_USE_ON_DEVICE_AI = "key_use_on_device_ai"
        const val KEY_SHOW_ON_DEVICE_DIAGNOSTICS = "key_show_on_device_diagnostics"
    }
}
