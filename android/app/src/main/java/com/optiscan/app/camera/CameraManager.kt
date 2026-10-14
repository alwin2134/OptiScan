package com.optiscan.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/**
 * CameraX controller managing camera binding, preview, and photo capture.
 */
class CameraManager {

    private val tag = "CameraManager"
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Bind CameraX lifecycle and connect Preview to PreviewView.
     */
    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraBound: ((Boolean) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                Log.i(tag, "CameraX successfully bound to lifecycle")
                onCameraBound?.invoke(true)
            } catch (e: Exception) {
                Log.e(tag, "Use case binding failed", e)
                onCameraBound?.invoke(false)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Capture a still photo and return as compressed JPEG ByteArray.
     */
    suspend fun capturePhoto(context: Context): Result<ByteArray> = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture
        if (capture == null) {
            continuation.resume(Result.failure(IllegalStateException("Camera capture is not initialized")))
            return@suspendCancellableCoroutine
        }

        val executor = ContextCompat.getMainExecutor(context)
        capture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val jpegBytes = imageProxyToJpegBytes(imageProxy)
                        continuation.resume(Result.success(jpegBytes))
                    } catch (e: Exception) {
                        continuation.resume(Result.failure(e))
                    } finally {
                        imageProxy.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed: ${exception.message}", exception)
                    continuation.resume(Result.failure(exception))
                }
            }
        )
    }

    /**
     * Convert ImageProxy buffer to JPEG byte array.
     */
    private fun imageProxyToJpegBytes(imageProxy: ImageProxy): ByteArray {
        val plane = imageProxy.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    /**
     * Utility to decode byte array into Android Bitmap.
     */
    fun bytesToBitmap(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Unbind all use cases and release camera provider.
     */
    fun unbind() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}
