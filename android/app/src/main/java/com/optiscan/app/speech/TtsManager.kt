package com.optiscan.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    var voiceFeedbackEnabled: Boolean = true
    var currentSpeechRate: Float = 1.0f
        private set

    var onSpeechStateChanged: ((isSpeaking: Boolean) -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(tag, "Default locale not supported, falling back to US English")
                tts?.language = Locale.US
            }
            tts?.setSpeechRate(currentSpeechRate)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onSpeechStateChanged?.invoke(false)
                }
            })

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
     * Set playback speech rate (e.g. 0.75f, 1.0f, 1.5f).
     */
    fun setSpeechRate(rate: Float) {
        currentSpeechRate = rate.coerceIn(0.5f, 2.5f)
        tts?.setSpeechRate(currentSpeechRate)
    }

    /**
     * Speak text aloud if voice feedback is enabled.
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!voiceFeedbackEnabled) {
            Log.d(tag, "Voice feedback is disabled in settings; suppressing speech")
            return
        }
        if (!isReady || text.isBlank()) {
            Log.w(tag, "TTS not ready or empty text requested")
            return
        }
        val utteranceId = "OptiScan_${System.currentTimeMillis()}"
        tts?.speak(text, queueMode, null, utteranceId)
    }

    /**
     * Stop any ongoing speech immediately.
     */
    fun stop() {
        tts?.stop()
        onSpeechStateChanged?.invoke(false)
    }

    /**
     * Check if TTS engine is currently vocalizing.
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
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
