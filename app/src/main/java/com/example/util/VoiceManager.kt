package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    // Callback to update VM speaking state
    var onSpeakingStateListener: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceManager", "US English is not supported or missing data")
            } else {
                isInitialized = true
                setupProgressListener()
            }
        } else {
            Log.e("VoiceManager", "TTS Initialization failed")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStateListener?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingStateListener?.invoke(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingStateListener?.invoke(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeakingStateListener?.invoke(false)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                onSpeakingStateListener?.invoke(false)
            }
        })
    }

    fun speak(text: String, assistantType: String, pitchFactor: Float = 1.0f, speedFactor: Float = 1.0f) {
        if (!isInitialized) {
            Log.e("VoiceManager", "TTS Not initialized, queuing failed")
            return
        }

        // Configure voice parameters according to characters & user preferences
        if (assistantType == "RED_QUEEN") {
            // Child-like, digital, slightly robotic and sharp
            tts?.setPitch(1.35f * pitchFactor)
            tts?.setSpeechRate(1.05f * speedFactor)
        } else {
            // John Wick: Deep, gravelly, quiet, deliberate and resolute
            tts?.setPitch(0.70f * pitchFactor)
            tts?.setSpeechRate(0.82f * speedFactor)
        }

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "companion_speech_id")
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "companion_speech_id")
    }

    fun stop() {
        tts?.stop()
        onSpeakingStateListener?.invoke(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
