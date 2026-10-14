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
        get() = (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")

    /**
     * Active backend base URL.
     * Automatically resolves to 10.0.2.2:8000 on emulators and 127.0.0.1:8000 on physical devices.
     */
    var activeBaseUrl: String = if (isEmulator) EMULATOR_BASE_URL else PHYSICAL_DEVICE_REVERSE_URL
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
        }
}
