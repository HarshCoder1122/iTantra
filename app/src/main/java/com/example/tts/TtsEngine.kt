package com.example.tts

import com.example.model.SupportedLanguage
import kotlinx.coroutines.flow.StateFlow

data class TtsModelInfo(
    val name: String = "AI4Bharat Indic-TTS (not yet loaded)",
    val runtime: String = "ONNX Runtime Mobile (FastPitch + HiFi-GAN)",
    val modelSizeMb: Float = 0f,
    val sampleRateHz: Int = 22050,
    val isReady: Boolean = false
)

interface TtsEngine {
    val isSpeaking: StateFlow<Boolean>
    val currentlyPlayingText: StateFlow<String?>
    val modelInfo: StateFlow<TtsModelInfo>

    fun speak(
        text: String,
        language: SupportedLanguage,
        isAlert: Boolean = false,
        onDone: () -> Unit = {}
    )

    /** Warms up [language]'s FastPitch/HiFi-GAN sessions in the background without
     *  speaking anything, so the model is already resident by the time the user
     *  actually presses "Test voice" or a packet in this language arrives — moves
     *  the cold-load cost off the interactive path instead of eliminating it. */
    fun preload(language: SupportedLanguage)

    fun stop()
    fun shutdown()
}
