package com.optiscan.app.utils

import com.optiscan.app.BuildConfig

/**
 * Networking configuration for backend communication.
 *
 * NOTE FOR EMULATOR TESTING:
 * When running inside the standard Android emulator, '10.0.2.2' maps to '127.0.0.1' (localhost)
 * of the host computer where the FastAPI backend runs.
 * If running on a physical Android device over Wi-Fi, change the base URL to your host's LAN IP
 * (e.g., "http://192.168.1.50:8000/").
 */
object NetworkConfig {
    /**
     * Default emulator host address mapping to host machine's localhost:8000.
     */
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8000/"

    /**
     * Mutable base URL allowing runtime configuration or BuildConfig default.
     */
    var activeBaseUrl: String = BuildConfig.DEFAULT_BACKEND_URL
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
        }
}
