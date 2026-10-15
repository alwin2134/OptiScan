package com.optiscan.app.utils

import android.os.Build

/**
 * Networking configuration for backend communication.
 *
 * EMULATOR NETWORKING:
 * On Android emulators, '10.0.2.2' maps to '127.0.0.1' (localhost) on the host computer.
 *
 * PHYSICAL DEVICE NETWORKING:
 * When debugging a physical Android device connected via USB, run:
 *   adb reverse tcp:8000 tcp:8000
 * This forwards port 8000 on the device to port 8000 on your host computer, allowing
 * 'http://127.0.0.1:8000/' to reach your local FastAPI server directly.
 */
object NetworkConfig {
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"
    const val PHYSICAL_DEVICE_REVERSE_URL = "http://127.0.0.1:8000/"

    /**
     * Heuristic to detect whether the app is executing inside an Android emulator.
     */
    val isEmulator: Boolean
        get() = (Build.BRAND?.startsWith("generic") == true && Build.DEVICE?.startsWith("generic") == true)
                || Build.FINGERPRINT?.startsWith("generic") == true
                || Build.HARDWARE?.contains("goldfish") == true
                || Build.HARDWARE?.contains("ranchu") == true
                || Build.MODEL?.contains("google_sdk") == true
                || Build.MODEL?.contains("Emulator") == true
                || Build.MODEL?.contains("Android SDK built for x86") == true
                || Build.MANUFACTURER?.contains("Genymotion") == true

    /**
     * Active backend base URL.
     * Automatically resolves to 10.0.2.2:8000 on emulators and 127.0.0.1:8000 on physical devices.
     */
    var activeBaseUrl: String = if (isEmulator) EMULATOR_BASE_URL else PHYSICAL_DEVICE_REVERSE_URL
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
        }
}
