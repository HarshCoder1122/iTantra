package com.example.stt

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.BundledModelManager
import com.example.model.SupportedLanguage
import com.example.model.VadStatus
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val TAG = "IndicSttEngine"
private const val SAMPLE_RATE = 16000
private const val VAD_WINDOW_SAMPLES = 512

/**
 * Real, fully offline Speech-To-Text engine: Silero VAD (genuine ONNX model) for
 * pause/utterance-boundary detection, feeding AI4Bharat IndicConformer (genuine
 * ONNX, nemo_ctc) for transcription — both run on-device via sherpa-onnx
 * (Apache-2.0), zero network calls, zero proprietary speech SDK.
 *
 * One [Vad] + [OfflineRecognizer] pair is loaded per active language, built from
 * whatever language packs [BundledModelManager] finds in assets/models. A language
 * with no shipped pack yet reports itself honestly as unavailable instead of
 * silently falling back to a fake result.
 */
class IndicSttEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    val bundledModelManager: BundledModelManager = BundledModelManager(context)
) : SttEngine {

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _vadStatus = MutableStateFlow(VadStatus.SILENCE)
    override val vadStatus: StateFlow<VadStatus> = _vadStatus.asStateFlow()

    private val _speechProbability = MutableStateFlow(0f)
    override val speechProbability: StateFlow<Float> = _speechProbability.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    override val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    override val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _finalizedUtterances = MutableSharedFlow<FinalizedUtterance>(extraBufferCapacity = 32)
    override val finalizedUtterances: SharedFlow<FinalizedUtterance> = _finalizedUtterances.asSharedFlow()

    private val _modelInfo = MutableStateFlow(
        SttModelInfo(
            name = "Loading offline models...",
            runtime = "sherpa-onnx (nemo_ctc) / ONNX Runtime",
            modelSizeMb = 0f,
            isQuantized = true,
            isLoaded = false,
            inferenceLatencyMs = 0
        )
    )
    override val modelInfo: StateFlow<SttModelInfo> = _modelInfo.asStateFlow()

    private var activeLanguage: SupportedLanguage = SupportedLanguage.HINDI
    private var loadedLanguageCode: String? = null
    private var vad: Vad? = null
    private var recognizer: OfflineRecognizer? = null
    private val modelLock = Mutex()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    init {
        // sherpa-onnx's JNI library resolves onnxruntime's C API (OrtGetApiBase) via the
        // process's already-loaded native libraries rather than a declared ELF dependency,
        // so libonnxruntime.so must be dlopen'd before libsherpa-onnx-jni.so ever is.
        // ai.onnxruntime.OrtEnvironment's own static initializer would normally do this,
        // but IndicTtsEngine may not have touched it yet by the time we get here.
        System.loadLibrary("onnxruntime")

        scope.launch(Dispatchers.IO) {
            bundledModelManager.loadAndVerifyBundledModels()
            ensureModelsForLanguage(activeLanguage)
        }
    }

    override fun setLanguage(language: SupportedLanguage) {
        activeLanguage = language
        scope.launch(Dispatchers.IO) { ensureModelsForLanguage(language) }
    }

    /**
     * Forces a fresh (re)load of [language]'s models and reports the real measured
     * load time via [modelInfo] — used by the Settings screen's "Test" button to
     * verify a language pack actually loads correctly on this device.
     */
    suspend fun reloadAndBenchmark(language: SupportedLanguage) {
        withContext(Dispatchers.IO) { ensureModelsForLanguage(language, force = true) }
    }

    /**
     * Copies `assets/models/$relPath` to internal storage the first time it's needed
     * (skipped on every subsequent load if the cached copy's size already matches, so
     * this is a one-time cost per install, not per language switch) and returns the
     * resulting absolute file path — see the mmap-vs-heap-buffer note in
     * [ensureModelsForLanguage] for why this matters on low-RAM devices.
     */
    private fun extractAssetToFile(relPath: String): String {
        val outFile = java.io.File(context.filesDir, "models_cache/$relPath")
        // Compare against the size BundledModelManager already computed while hashing
        // this asset, rather than assets.openFd() — that call throws for any asset the
        // build didn't list under androidResources.noCompress (tokens.txt, frontend.json
        // etc. are still stored zip-compressed, only *.onnx/*.bin are exempted).
        val expectedSize = bundledModelManager.verifiedAssets.value[relPath]?.sizeBytes
        if (!outFile.exists() || (expectedSize != null && outFile.length() != expectedSize)) {
            outFile.parentFile?.mkdirs()
            // 1MB buffer instead of copyTo()'s 8KB default — the STT model alone is
            // ~141MB, and the syscall overhead of an 8KB buffer is real time on a
            // first-run/first-language-switch extraction.
            context.assets.open("models/$relPath").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 20) }
            }
        }
        return outFile.absolutePath
    }

    /** (Re)builds the Vad + OfflineRecognizer for [language] if not already loaded. */
    private suspend fun ensureModelsForLanguage(language: SupportedLanguage, force: Boolean = false) {
        modelLock.withLock {
            if (!force && loadedLanguageCode == language.code && recognizer != null && vad != null) return

            val pack = bundledModelManager.languagePacks.value[language.code]
            val sttAsset = pack?.stt
            val vadAsset = bundledModelManager.vadModel.value

            if (sttAsset == null || vadAsset == null) {
                Log.w(TAG, "No offline STT pack shipped for '${language.code}' yet")
                recognizer?.release()
                vad?.release()
                recognizer = null
                vad = null
                loadedLanguageCode = null
                _modelInfo.value = _modelInfo.value.copy(
                    name = "No offline model for ${language.englishName}",
                    isLoaded = false,
                )
                return
            }

            try {
                val start = System.nanoTime()

                // Load from real files on internal storage rather than via AssetManager:
                // sherpa-onnx's newFromAsset() path reads the whole model into a native
                // heap buffer through the Android asset API (necessary since assets live
                // inside the APK's zip, not on a real filesystem path); newFromFile() lets
                // the underlying ONNX Runtime session open the file directly instead. On a
                // 141MB STT model that's a real difference on a 2GB-RAM device, so we pay
                // a one-time extract-to-disk cost (skipped on every load after the first)
                // to get there. See extractAssetToFile() below.
                val vadModelFile = extractAssetToFile(vadAsset.modelPath)
                val sttModelFile = extractAssetToFile(sttAsset.modelPath)
                val sttTokensFile = extractAssetToFile(sttAsset.tokensPath)

                val newVad = Vad(
                    assetManager = null,
                    config = VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = vadModelFile,
                            threshold = 0.5f,
                            minSilenceDuration = 0.5f, // pause length that ends a sentence
                            minSpeechDuration = 0.25f,
                            windowSize = vadAsset.windowSizeSamples,
                        ),
                        sampleRate = vadAsset.sampleRateHz,
                        numThreads = 1,
                        provider = "cpu",
                    ),
                )

                val newRecognizer = OfflineRecognizer(
                    assetManager = null,
                    config = OfflineRecognizerConfig(
                        featConfig = FeatureConfig(
                            sampleRate = sttAsset.sampleRateHz,
                            featureDim = sttAsset.featureDim,
                        ),
                        modelConfig = OfflineModelConfig(
                            nemo = OfflineNemoEncDecCtcModelConfig(model = sttModelFile),
                            tokens = sttTokensFile,
                            // 1 thread: low-end SoCs typically have 4-8 cores shared with
                            // audio capture + UI + the mesh transport's own IO threads, so
                            // a second ORT worker thread buys little latency at the cost of
                            // extra scratch-buffer memory and scheduling contention.
                            numThreads = 1,
                        ),
                        decodingMethod = "greedy_search",
                    ),
                )

                val loadMs = (System.nanoTime() - start) / 1_000_000L

                recognizer?.release()
                vad?.release()
                recognizer = newRecognizer
                vad = newVad
                loadedLanguageCode = language.code

                _modelInfo.value = SttModelInfo(
                    name = sttAsset.name,
                    runtime = "sherpa-onnx nemo_ctc / ONNX Runtime (${sttAsset.architecture})",
                    modelSizeMb = bundledModelManager.verifiedAssets.value[sttAsset.modelPath]
                        ?.let { it.sizeBytes / 1_000_000f } ?: 0f,
                    isQuantized = true,
                    isLoaded = true,
                    inferenceLatencyMs = loadMs.toInt(),
                )
                Log.i(TAG, "Loaded real STT+VAD for '${language.code}' in ${loadMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load STT/VAD models for '${language.code}'", e)
                _modelInfo.value = _modelInfo.value.copy(name = "Failed to load model: ${e.message}", isLoaded = false)
            }
        }
    }

    override fun startListening(language: SupportedLanguage) {
        if (_isListening.value) return
        activeLanguage = language

        val hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasRecordPermission) {
            Log.w(TAG, "RECORD_AUDIO permission missing; cannot record")
            _partialTranscript.value = "Microphone permission required"
            return
        }

        _isListening.value = true
        _vadStatus.value = VadStatus.SILENCE
        _speechProbability.value = 0f
        _audioLevel.value = 0f
        _partialTranscript.value = ""

        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            ensureModelsForLanguage(language)
            if (recognizer == null || vad == null) {
                _partialTranscript.value = "No offline speech model installed for ${language.englishName}"
                _isListening.value = false
                return@launch
            }
            runCaptureLoop()
        }
    }

    private suspend fun runCaptureLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(VAD_WINDOW_SAMPLES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                _isListening.value = false
                return
            }

            audioRecord?.startRecording()
            val shortBuffer = ShortArray(VAD_WINDOW_SAMPLES)

            while (scope.isActive && _isListening.value) {
                val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                if (read <= 0) continue

                var sumSquares = 0.0
                val floatSamples = FloatArray(read)
                for (i in 0 until read) {
                    val s = shortBuffer[i]
                    floatSamples[i] = s / 32768.0f
                    sumSquares += (s.toDouble() * s.toDouble())
                }
                _audioLevel.value = (sqrt(sumSquares / read) / 6000.0).toFloat().coerceIn(0.05f, 1.0f)

                val currentVad = vad ?: break
                currentVad.acceptWaveform(floatSamples)

                val detected = currentVad.isSpeechDetected()
                _vadStatus.value = if (detected) VadStatus.SPEECH_DETECTED else VadStatus.SILENCE
                _speechProbability.value = if (detected) 1f else 0f

                drainCompletedSegments(currentVad)
            }

            // Flush whatever partial utterance was still being spoken when stopped.
            vad?.flush()
            vad?.let { drainCompletedSegments(it) }
        } catch (e: CancellationException) {
            // expected on stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Capture loop error: ${e.message}")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                // ignore
            }
            audioRecord = null
            _vadStatus.value = VadStatus.SILENCE
            _speechProbability.value = 0f
            _audioLevel.value = 0f
        }
    }

    private fun drainCompletedSegments(currentVad: Vad) {
        val activeRecognizer = recognizer ?: return
        while (!currentVad.empty()) {
            val segment = currentVad.front()
            currentVad.pop()
            if (segment.samples.isEmpty()) continue

            val decodeStart = System.nanoTime()
            val stream = activeRecognizer.createStream()
            stream.acceptWaveform(segment.samples, SAMPLE_RATE)
            activeRecognizer.decode(stream)
            val result = activeRecognizer.getResult(stream)
            stream.release()
            val decodeMs = (System.nanoTime() - decodeStart) / 1_000_000L

            _modelInfo.value = _modelInfo.value.copy(inferenceLatencyMs = decodeMs.toInt())

            val text = result.text.trim()
            if (text.isNotBlank()) {
                val durationMs = (segment.samples.size * 1000L / SAMPLE_RATE).coerceAtLeast(200L)
                scope.launch {
                    _finalizedUtterances.emit(
                        FinalizedUtterance(
                            text = text,
                            language = activeLanguage,
                            durationMs = durationMs,
                            // Greedy CTC decoding (as used here) doesn't emit a calibrated
                            // per-utterance confidence score, so this isn't a real probability —
                            // it only distinguishes "recognizer returned text" from "returned nothing".
                            confidence = 1.0f,
                        )
                    )
                }
            }
        }
    }

    override fun stopListening() {
        if (!_isListening.value) return
        _isListening.value = false
        // runCaptureLoop's finally block + the flush() call above handle cleanup
        // and emitting any trailing utterance once the loop notices isListening is false.
    }

    override fun forceFinalizeSentence() {
        val currentVad = vad ?: return
        currentVad.flush()
        drainCompletedSegments(currentVad)
    }
}
