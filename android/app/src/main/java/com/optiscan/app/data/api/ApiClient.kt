package com.optiscan.app.data.api

import com.optiscan.app.utils.NetworkConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton factory creating configured Retrofit instances and API services.
 */
object ApiClient {

    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Avoid logging full binary image multipart payloads (prevents leaking sensitive captures & buffer overflow)
            level = if (com.optiscan.app.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Create an instance of OptiScanApi pointing to the configured base URL.
     */
    fun createService(baseUrl: String = NetworkConfig.activeBaseUrl): OptiScanApi {
        val safeUrl = if (NetworkConfig.isValidUrl(baseUrl)) {
            NetworkConfig.normalizeUrl(baseUrl)
        } else {
            "http://127.0.0.1:8000/"
        }
        return Retrofit.Builder()
            .baseUrl(safeUrl)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OptiScanApi::class.java)
    }
}
