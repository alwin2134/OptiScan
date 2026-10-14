package com.optiscan.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech manager providing accessible spoken feedback to visually impaired users.
 */
class TtsManager(
    context: Context,
    private val onInitComplete: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private val tag = "TtsManager"
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isReady: Boolean = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Default locale not supported, falling back to US English")
                tts?.language = Locale.US
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            isReady = true
            Log.i(tag, "TTS initialized successfully")
            onInitComplete?.invoke(true)
        } else {
            Log.e(tag, "TTS initialization failed with status: $status")
            isReady = false
            onInitComplete?.invoke(false)
        }
    }

    /**
     * Speak text aloud, interrupting any previous speech (QUEUE_FLUSH).
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isReady || text.isBlank()) {
            Log.w(tag, "TTS not ready or empty text requested")
            return
        }
        tts?.speak(text, queueMode, null, "OptiScanUtterance_${System.currentTimeMillis()}")
    }

    /**
     * Stop any ongoing speech.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Release TTS engine resources.
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
