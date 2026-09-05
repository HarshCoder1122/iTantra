package com.example.stt

import com.example.model.SupportedLanguage
import com.example.model.VadStatus
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class FinalizedUtterance(
    val text: String,
    val language: SupportedLanguage,
    val durationMs: Long,
    val confidence: Float = 0.94f
)

data class SttModelInfo(
    val name: String = "AI4Bharat IndicConformer (not yet loaded)",
    val runtime: String = "sherpa-onnx nemo_ctc / ONNX Runtime",
    val modelSizeMb: Float = 0f,
    val isQuantized: Boolean = true,
    val isLoaded: Boolean = false,
    val inferenceLatencyMs: Int = 0
)

interface SttEngine {
    val isListening: StateFlow<Boolean>
    val vadStatus: StateFlow<VadStatus>
    val speechProbability: StateFlow<Float>
    val audioLevel: StateFlow<Float> // 0f..1f for dynamic live HUD waveform
    val partialTranscript: StateFlow<String>
    val finalizedUtterances: SharedFlow<FinalizedUtterance>
    val modelInfo: StateFlow<SttModelInfo>

    fun startListening(language: SupportedLanguage)
    fun stopListening()
    fun forceFinalizeSentence()
    fun setLanguage(language: SupportedLanguage)
}
