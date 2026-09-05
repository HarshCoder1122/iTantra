package com.example.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.audio.AlertAudioManager
import com.example.model.BundledModelManager
import com.example.model.SupportedLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

private const val TAG = "IndicTtsEngine"
/** Hard cap on characters per inference chunk — see [IndicTtsEngine.splitIntoSpeakableChunks]. */
private const val MAX_CHUNK_CHARS = 140

/** Parsed assets/models/<lang>/tts/frontend.json — the character vocab + text
 * normalization the checkpoint was actually trained with (dumped straight from
 * the real tokenizer at export time, not reverse-engineered). */
private data class TtsFrontend(
    val charToId: Map<String, Int>,
    val sampleRateHz: Int,
    val defaultSpeakerId: Int,
)

/**
 * Real, fully offline Text-To-Speech engine: our own exported ONNX graphs of the
 * real AI4Bharat Indic-TTS checkpoint (FastPitch acoustic model -> HiFi-GAN
 * vocoder), run on-device via ONNX Runtime Mobile — zero network calls, zero
 * proprietary TTS engine, zero canned/formant-synth fallback.
 */
class IndicTtsEngine(
    private val context: Context,
    private val alertAudioManager: AlertAudioManager,
    private val scope: CoroutineScope,
    val bundledModelManager: BundledModelManager = BundledModelManager(context)
) : TtsEngine {

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlyPlayingText = MutableStateFlow<String?>(null)
    override val currentlyPlayingText: StateFlow<String?> = _currentlyPlayingText.asStateFlow()

    private val _modelInfo = MutableStateFlow(
        TtsModelInfo(
            name = "Loading offline TTS models...",
            runtime = "ONNX Runtime Mobile (FastPitch + HiFi-GAN)",
            modelSizeMb = 0f,
            sampleRateHz = 22050,
            isReady = false
        )
    )
    override val modelInfo: StateFlow<TtsModelInfo> = _modelInfo.asStateFlow()

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var loadedLanguageCode: String? = null
    private var fastpitchSession: OrtSession? = null
    private var hifiganSession: OrtSession? = null
    private var frontend: TtsFrontend? = null
    private val modelLock = Mutex()

    private var audioTrack: AudioTrack? = null
    private var speakJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            bundledModelManager.loadAndVerifyBundledModels()
        }
    }

    /** Same extract-once-then-load-by-path pattern as IndicSttEngine.extractAssetToFile —
     *  see the comment in ensureModelsForLanguage() for why. */
    private fun extractAssetToFile(relPath: String): String {
        val outFile = java.io.File(context.filesDir, "models_cache/$relPath")
        val expectedSize = bundledModelManager.verifiedAssets.value[relPath]?.sizeBytes
        if (!outFile.exists() || (expectedSize != null && outFile.length() != expectedSize)) {
            outFile.parentFile?.mkdirs()
            // 1MB buffer instead of copyTo()'s 8KB default — these are 60-120MB model
            // files, and the syscall overhead of an 8KB buffer is real time on a
            // first-run/first-language-switch extraction.
            context.assets.open("models/$relPath").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 20) }
            }
        }
        return outFile.absolutePath
    }

    private suspend fun ensureModelsForLanguage(language: SupportedLanguage) {
        modelLock.withLock {
            if (loadedLanguageCode == language.code && fastpitchSession != null && hifiganSession != null) return

            val pack = bundledModelManager.languagePacks.value[language.code]
            val ttsAsset = pack?.tts
            if (ttsAsset == null) {
                Log.w(TAG, "No offline TTS pack shipped for '${language.code}' yet")
                fastpitchSession?.close()
                hifiganSession?.close()
                fastpitchSession = null
                hifiganSession = null
                frontend = null
                loadedLanguageCode = null
                _modelInfo.value = _modelInfo.value.copy(name = "No offline TTS model for ${language.englishName}", isReady = false)
                return
            }

            try {
                val start = System.nanoTime()

                // Extract once to internal storage and load by file path rather than
                // reading the whole ~60-120MB model into a JVM byte[] and handing that
                // across JNI: createSession(byte[]) means both the Java array AND ONNX
                // Runtime's own parsed copy are resident at once, which is exactly the
                // kind of transient spike a 2GB-RAM device can't absorb. Loading by path
                // lets ORT read the file itself instead of going through the JVM heap.
                val fpPath = extractAssetToFile(ttsAsset.acousticModelPath)
                val hgPath = extractAssetToFile(ttsAsset.vocoderPath)
                val frontendJson = context.assets.open("models/${ttsAsset.frontendConfigPath}")
                    .bufferedReader().use { it.readText() }

                val root = JSONObject(frontendJson)
                val vocabArray = root.getJSONArray("vocab")
                val charToId = mutableMapOf<String, Int>()
                for (i in 0 until vocabArray.length()) {
                    charToId[vocabArray.getString(i)] = i
                }
                val newFrontend = TtsFrontend(
                    charToId = charToId,
                    sampleRateHz = root.optInt("sampleRateHz", 22050),
                    defaultSpeakerId = root.optInt("defaultSpeakerId", 0),
                )

                // 1 thread each: two small sessions running back-to-back per utterance,
                // not a latency-critical streaming path — extra ORT worker threads would
                // only add scratch-buffer memory and scheduling overhead on weak CPUs.
                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(1)
                    setInterOpNumThreads(1)
                    setMemoryPatternOptimization(true)
                }
                val newFastpitch = ortEnv.createSession(fpPath, sessionOptions)
                val newHifigan = ortEnv.createSession(hgPath, sessionOptions)

                val loadMs = (System.nanoTime() - start) / 1_000_000L

                fastpitchSession?.close()
                hifiganSession?.close()
                fastpitchSession = newFastpitch
                hifiganSession = newHifigan
                frontend = newFrontend
                loadedLanguageCode = language.code

                _modelInfo.value = TtsModelInfo(
                    name = ttsAsset.name,
                    runtime = "ONNX Runtime Mobile (FastPitch + HiFi-GAN)",
                    modelSizeMb = bundledModelManager.verifiedAssets.value[ttsAsset.acousticModelPath]
                        ?.let { it.sizeBytes / 1_000_000f } ?: 0f,
                    sampleRateHz = newFrontend.sampleRateHz,
                    isReady = true,
                )
                Log.i(TAG, "Loaded real TTS for '${language.code}' in ${loadMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TTS models for '${language.code}'", e)
                _modelInfo.value = _modelInfo.value.copy(name = "Failed to load model: ${e.message}", isReady = false)
            }
        }
    }

    /** multilingual_cleaners(): NFC normalize, lowercase, ;/-/: -> ,/space/, ,
     * strip <>()[]" , collapse whitespace — exactly what the checkpoint was
     * trained against (see tools/model_conversion/export_tts.py). */
    private fun cleanText(text: String): String {
        var t = Normalizer.normalize(text, Normalizer.Form.NFC)
        t = t.lowercase()
        t = t.replace(";", ",").replace("-", " ").replace(":", ",")
        t = t.replace(Regex("[<>()\\[\\]\"]+"), "")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    private fun textToIds(text: String, vocab: Map<String, Int>): LongArray {
        val cleaned = cleanText(text)
        val ids = mutableListOf<Long>()
        for (ch in cleaned) {
            val id = vocab[ch.toString()]
            if (id != null) {
                ids.add(id.toLong())
            } else {
                Log.d(TAG, "Skipping out-of-vocab character: '$ch'")
            }
        }
        return ids.toLongArray()
    }

    /** Sentence-ish split so one inference call never has to synthesize an entire
     *  multi-sentence message at once. This is the main lever for perceived TTS
     *  speed: playback of chunk 1 starts as soon as it's synthesized, while later
     *  chunks are still being computed underneath it (see [speak]) — a long alert
     *  starts being heard in "one sentence's worth" of latency instead of "the
     *  whole message's worth". It also bounds peak tensor size per inference call,
     *  which matters for OOM/ANR risk on 2GB-RAM devices given a very long message. */
    private fun splitIntoSpeakableChunks(text: String): List<String> {
        val sentenceBoundary = Regex("(?<=[।॥.!?])\\s+")
        val sentences = text.split(sentenceBoundary).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= MAX_CHUNK_CHARS) {
                chunks += sentence
                continue
            }
            // Very long, unpunctuated sentence: fall back to hard word-boundary slices
            // rather than handing FastPitch/HiFi-GAN an unbounded sequence length.
            var remaining = sentence
            while (remaining.length > MAX_CHUNK_CHARS) {
                var cut = remaining.lastIndexOf(' ', MAX_CHUNK_CHARS)
                if (cut <= 0) cut = MAX_CHUNK_CHARS
                chunks += remaining.substring(0, cut).trim()
                remaining = remaining.substring(cut).trim()
            }
            if (remaining.isNotEmpty()) chunks += remaining
        }
        return chunks.ifEmpty { listOf(text) }
    }

    override fun speak(text: String, language: SupportedLanguage, isAlert: Boolean, onDone: () -> Unit) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onDone()
            return
        }

        // A new utterance always preempts whatever this engine was still synthesizing
        // or playing — without this, speak() calls that arrive faster than playback
        // (e.g. a burst of incoming packets) would pile up as concurrent AudioTracks
        // racing each other instead of the newest one winning.
        speakJob?.cancel()

        _currentlyPlayingText.value = trimmed
        _isSpeaking.value = true

        speakJob = scope.launch(Dispatchers.Default) {
            ensureModelsForLanguage(language)
            val fastpitch = fastpitchSession
            val hifigan = hifiganSession
            val fe = frontend

            if (fastpitch == null || hifigan == null || fe == null) {
                Log.w(TAG, "No offline TTS model installed for ${language.englishName}")
                finishSpeaking(isAlert, onDone)
                return@launch
            }

            var track: AudioTrack? = null
            try {
                if (isAlert) {
                    alertAudioManager.lockAudioFocusForAlert()
                    alertAudioManager.playEmergencySirenTone(durationMs = 1200)
                }

                val chunks = splitIntoSpeakableChunks(trimmed)
                track = openStreamingTrack(fe.sampleRateHz, isAlert)
                audioTrack = track
                track.play()

                var totalFramesWritten = 0
                for (chunk in chunks) {
                    if (!isActive) break
                    val ids = textToIds(chunk, fe.charToId)
                    if (ids.isEmpty()) {
                        Log.d(TAG, "No synthesizable characters in chunk '$chunk'")
                        continue
                    }
                    val wav = synthesize(fastpitch, hifigan, ids, fe.defaultSpeakerId)
                    totalFramesWritten += writeStreamingPcm(track, wav)
                }

                // Writes above only guarantee the audio was accepted into the track's
                // buffer, not that it has actually been heard yet — wait for real
                // playback to catch up before reporting done.
                while (isActive && track.playbackHeadPosition < totalFramesWritten) {
                    delay(20)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Synthesis failed for '$trimmed'", e)
            } finally {
                try { track?.stop() } catch (e: Exception) {}
                try { track?.release() } catch (e: Exception) {}
                if (audioTrack === track) audioTrack = null
                finishSpeaking(isAlert, onDone)
            }
        }
    }

    private fun finishSpeaking(isAlert: Boolean, onDone: () -> Unit) {
        _isSpeaking.value = false
        _currentlyPlayingText.value = null
        if (isAlert) alertAudioManager.releaseAlertAudioFocus()
        onDone()
    }

    /** input_ids [1,N] + speaker_id [1] -> mel [1,T,80] (FastPitch), transposed
     * to [1,80,T] and fed to HiFi-GAN -> wav [1,1,samples]. Both graphs are
     * genuinely dynamic-shaped (see export_tts.py) — no padding, no bucketing. */
    private fun synthesize(
        fastpitch: OrtSession,
        hifigan: OrtSession,
        ids: LongArray,
        speakerId: Int,
    ): FloatArray {
        val mel: Array<FloatArray> = OnnxTensor.createTensor(ortEnv, arrayOf(ids)).use { inputTensor ->
            OnnxTensor.createTensor(ortEnv, longArrayOf(speakerId.toLong())).use { speakerTensor ->
                fastpitch.run(mapOf("input_ids" to inputTensor, "speaker_id" to speakerTensor)).use { results ->
                    @Suppress("UNCHECKED_CAST")
                    val melBatch = results[0].value as Array<Array<FloatArray>>
                    melBatch[0] // [T, 80]
                }
            }
        }

        val numMels = mel[0].size
        val numFrames = mel.size
        val melChw = Array(1) { Array(numMels) { m -> FloatArray(numFrames) { t -> mel[t][m] } } }

        val wavBatch: Array<Array<FloatArray>> = OnnxTensor.createTensor(ortEnv, melChw).use { melTensor ->
            hifigan.run(mapOf("mel" to melTensor)).use { results ->
                @Suppress("UNCHECKED_CAST")
                results[0].value as Array<Array<FloatArray>>
            }
        }
        return wavBatch[0][0]
    }

    /** MODE_STREAM (not MODE_STATIC) so chunks can be handed to the track as each one
     *  finishes synthesizing, rather than needing the entire utterance's PCM buffered
     *  up front before any sound can start. */
    private fun openStreamingTrack(sampleRateHz: Int, isAlert: Boolean): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(sampleRateHz, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, minBuf * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Blocking write of one chunk's PCM16 samples; returns frame count written so
     *  the caller can track total frames against [AudioTrack.getPlaybackHeadPosition]. */
    private fun writeStreamingPcm(track: AudioTrack, samples: FloatArray): Int {
        val pcm = ShortArray(samples.size) { i ->
            (max(-1f, min(1f, samples[i])) * 32767f).toInt().toShort()
        }
        var offset = 0
        while (offset < pcm.size) {
            val written = track.write(pcm, offset, pcm.size - offset)
            if (written <= 0) break
            offset += written
        }
        return pcm.size
    }

    override fun stop() {
        speakJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
        _isSpeaking.value = false
        _currentlyPlayingText.value = null
        alertAudioManager.releaseAlertAudioFocus()
        alertAudioManager.stopHaptics()
    }

    override fun shutdown() {
        stop()
        fastpitchSession?.close()
        hifiganSession?.close()
        fastpitchSession = null
        hifiganSession = null
    }
}
