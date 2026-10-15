package com.optiscan.app

import com.optiscan.app.navigation.AppLanguage
import com.optiscan.app.navigation.AppSettings
import com.optiscan.app.navigation.Screen
import com.optiscan.app.ondevice.ConversationTurn
import com.optiscan.app.ondevice.DeviceCapabilityInfo
import com.optiscan.app.ondevice.DiagnosticInfo
import com.optiscan.app.ondevice.InferenceState
import com.optiscan.app.ondevice.ModelDownloadState
import com.optiscan.app.ui.localization.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying On-Device AI models, navigation routing, settings toggles,
 * localized strings, and state machine transitions.
 */
class OnDeviceAiTest {

    @Test
    fun testAppSettingsOnDeviceDefaults() {
        val defaultSettings = AppSettings()
        assertFalse("On-device AI should be disabled by default so cloud is default", defaultSettings.useOnDeviceAi)
        assertFalse("Diagnostics should be hidden by default", defaultSettings.showOnDeviceDiagnostics)

        val updated = defaultSettings.copy(useOnDeviceAi = true, showOnDeviceDiagnostics = true)
        assertTrue(updated.useOnDeviceAi)
        assertTrue(updated.showOnDeviceDiagnostics)
    }

    @Test
    fun testNavigationScreenOnDeviceRoute() {
        val screen: Screen = Screen.OnDeviceConversation
        assertEquals(Screen.OnDeviceConversation, screen)
    }

    @Test
    fun testConversationTurn() {
        val userTurn = ConversationTurn(text = "What is in front of me?", isUser = true)
        assertTrue(userTurn.isUser)
        assertEquals("What is in front of me?", userTurn.text)
        assertTrue(userTurn.timestampMs > 0)

        val assistantTurn = ConversationTurn(text = "You are facing a clear hallway.", isUser = false)
        assertFalse(assistantTurn.isUser)
        assertEquals("You are facing a clear hallway.", assistantTurn.text)
    }

    @Test
    fun testDeviceCapabilitySuitability() {
        val highCap = DeviceCapabilityInfo(
            totalRamMb = 6144L,
            availableStorageMb = 10240L,
            abi = "arm64-v8a",
            meetsMinimumRam = true,
            hasArm64 = true
        )
        assertTrue(highCap.meetsMinimumRam)
        assertTrue(highCap.hasArm64)
        assertEquals("arm64-v8a", highCap.abi)

        val lowCap = DeviceCapabilityInfo(
            totalRamMb = 2048L,
            availableStorageMb = 4096L,
            abi = "armeabi-v7a",
            meetsMinimumRam = false,
            hasArm64 = false
        )
        assertFalse(lowCap.meetsMinimumRam)
        assertFalse(lowCap.hasArm64)
    }

    @Test
    fun testModelDownloadStates() {
        val idle = ModelDownloadState.Idle
        assertEquals(ModelDownloadState.Idle, idle)

        val progress = ModelDownloadState.Downloading(
            progressPercent = 45,
            downloadedMb = 675.0f,
            totalMb = 1500.0f
        )
        assertEquals(45, progress.progressPercent)
        assertEquals(675.0f, progress.downloadedMb, 0.01f)
        assertEquals(1500.0f, progress.totalMb, 0.01f)

        val verifying = ModelDownloadState.Verifying
        assertEquals(ModelDownloadState.Verifying, verifying)

        val ready = ModelDownloadState.Ready
        assertEquals(ModelDownloadState.Ready, ready)

        val failed = ModelDownloadState.Failed("Insufficient storage space")
        assertEquals("Insufficient storage space", failed.reason)

        val cancelled = ModelDownloadState.Cancelled
        assertEquals(ModelDownloadState.Cancelled, cancelled)
    }

    @Test
    fun testDiagnosticInfoMetrics() {
        val diag = DiagnosticInfo(
            modelLoadTimeMs = 1250L,
            lastInferenceMs = 2100L
        )
        assertEquals(1250L, diag.modelLoadTimeMs)
        assertEquals(2100L, diag.lastInferenceMs)
    }

    @Test
    fun testInferenceStateTransitions() {
        val unloaded = InferenceState.Unloaded
        assertEquals(InferenceState.Unloaded, unloaded)

        val loading = InferenceState.Loading
        assertEquals(InferenceState.Loading, loading)

        val ready = InferenceState.Ready
        assertEquals(InferenceState.Ready, ready)

        val generating = InferenceState.Generating(partialText = "Describe this medicine box")
        assertEquals("Describe this medicine box", generating.partialText)

        val failed = InferenceState.Failed("Out of memory")
        assertEquals("Out of memory", failed.reason)
    }

    @Test
    fun testOnDeviceAiStringsLocalization() {
        val en = AppStrings.get(AppLanguage.ENGLISH)
        assertNotNull(en.onDeviceAiSettingsTitle)
        assertNotNull(en.onDeviceAiSettingsSubtitle)
        assertNotNull(en.onDeviceAiModeButton)
        assertNotNull(en.onDeviceAiModeSubtitle)
        assertNotNull(en.onDeviceAiPrivacyBadge)
        assertTrue(en.onDeviceAiPrivacyBadge.contains("On-device"))

        val hi = AppStrings.get(AppLanguage.HINDI)
        assertNotNull(hi.onDeviceAiSettingsTitle)
        assertNotNull(hi.onDeviceAiSettingsSubtitle)
        assertNotNull(hi.onDeviceAiModeButton)
        assertNotNull(hi.onDeviceAiModeSubtitle)
        assertNotNull(hi.onDeviceAiPrivacyBadge)
        assertTrue(hi.onDeviceAiSettingsTitle.contains("Qwen2-VL"))
    }
}
