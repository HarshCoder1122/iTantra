# Real AI4Bharat on-device STT/TTS — progress checkpoint

Working per the approved plan (fully on-device, no cloud, per SIH #26173 rules).
Build machine work happens under `C:\mlbuild` (short path, avoids Windows MAX_PATH
issues with onnx/pip package data); only final small artifacts get copied into
this repo's `app/src/main/assets/models/`.

## Done

- [x] Extracted `itantra.zip` into this real project folder (`SIH/itantra/`).
- [x] `C:\mlbuild\venv` — Python 3.12 venv with onnx, onnxruntime, huggingface_hub,
      sherpa-onnx (desktop, for verification), numpy, soundfile.
- [x] STT (Hindi): downloaded `trysem/indicconformer-120m-onnx` hi/model.onnx (493MB)
      + hi/vocab.json from HF. Converted via `tools/model_conversion/convert_stt.py`
      → `C:\mlbuild\converted\hi\stt\{model.int8.onnx (141MB), tokens.txt (5632 tokens
      + <blk> at id 5632), model.onnx (fp32, not shipped)}`. Verified loads + runs
      via sherpa-onnx Python (`verify_stt.py`) — graph/metadata/tokens all consistent,
      returns '' on synthetic silence (no crash). NOT YET verified against real speech
      audio (no Hindi WAV sample found yet — plan is to close the loop once TTS output
      exists: synthesize Hindi speech, feed it back into this STT model, compare text).
- [x] Cloned `k2-fsa/sherpa-onnx` into `C:\mlbuild\sherpa-onnx`.
- [x] Downloaded prebuilt native libs `sherpa-onnx-v1.13.7-android.tar.bz2` (has
      arm64-v8a/armeabi-v7a/x86/x86_64 `.so`s) — copied `libonnxruntime.so` +
      `libsherpa-onnx-jni.so` per ABI into
      `sherpa-onnx/android/SherpaOnnxAar/sherpa_onnx/src/main/jniLibs/<abi>/`.
  - Building the actual `.aar` via `./gradlew :sherpa_onnx:assembleRelease` failed
    twice on `services.gradle.org` SocketTimeoutException (Java's HttpURLConnection
    couldn't reach it even though `curl` could — looks like a JVM-specific network
    quirk on this machine, not a real outage). **Fix**: downloaded Gradle 8.6 standalone
    directly via curl (`C:\mlbuild\gradle-dist\gradle-8.6\bin\gradle.bat`) and invoke
    that directly instead of `./gradlew`, bypassing the wrapper's self-download.
  - Second problem found: the `sherpa_onnx` module's Kotlin API source files
    (`.../src/main/java/com/k2fsa/sherpa/onnx/*.kt`) are **symlinks in the upstream
    git repo**, but Windows git (without `core.symlinks`/developer mode) checked
    them out as literal text files whose only content is the relative symlink
    target path (e.g. `OfflineRecognizer.kt` contained just the string
    `../../../../../../../../../../sherpa-onnx/kotlin-api/OfflineRecognizer.kt`).
    Kotlinc then failed with a wall of "expecting a top level declaration" errors
    (it was compiling that path string as source code). **Fixed** by copying the
    real files from `sherpa-onnx/sherpa-onnx/kotlin-api/*.kt` over all 22 broken
    stub files in the AAR module directly (`cp` each by matching filename).
    Re-running the build now (`aar_build2.log`).
  - Also found: the two TTS checkpoint zips (`hi.zip`, `en.zip`) that finished
    downloading were both **corrupted** (`unzip -t` failed) — root cause was a
    `curl -C -` resume across a connection that had reset mid-transfer earlier.
    Fixed by deleting both and redownloading fresh (not resumed) with
    `--retry 8 --retry-all-errors`. Re-downloading now.
  - Once the AAR build succeeds, check
    `sherpa-onnx/android/SherpaOnnxAar/sherpa_onnx/build/outputs/aar/sherpa_onnx-release.aar`
    for the finished artifact, then copy it into `itantra/app/libs/`.

## Update — STT side is now fully rewired (real, not fake)

- [x] Copied real converted Hindi STT model into the actual app:
      `app/src/main/assets/models/hi/stt/{model.int8.onnx (141MB), tokens.txt}`.
- [x] Downloaded the real Silero VAD ONNX model (643KB, matches upstream) into
      `app/src/main/assets/models/vad/silero_vad.onnx`.
- [x] Rewrote `app/src/main/assets/models/model_manifest.json` (schemaVersion 2):
      real, data-driven, per-language `stt`/`tts` asset paths — no fabricated
      checksums/status strings. Deleted the old fake `.bin`/`.json` placeholder files.
- [x] Rewrote `model/BundledModelManager.kt`: parses the new manifest, computes
      real streamed SHA-256 + size for every asset it references (no fake
      "benchmark" byte-accumulator loop anymore — that's gone).
- [x] Rewrote `stt/IndicSttEngine.kt` completely: real sherpa-onnx `Vad` (Silero,
      real pause/endpoint detection) feeding a real sherpa-onnx `OfflineRecognizer`
      (`nemo_ctc`, our converted AI4Bharat IndicConformer). No more Android
      `SpeechRecognizer`. Deleted `vad/SileroVadDetector.kt` (the fake DSP
      heuristic) entirely — sherpa-onnx's real Vad replaces it.
- [x] Added `com.microsoft.onnxruntime:onnxruntime-android:1.22.0` (Maven Central,
      MIT) and the built `sherpa-onnx-1.13.7.aar` (copied to `app/libs/`) to
      `app/build.gradle.kts` + `gradle/libs.versions.toml`, plus the required
      `androidResources { noCompress += listOf("onnx","bin") }` so AAPT doesn't
      corrupt the model files' byte alignment.
- [x] Updated `viewmodel/MissionControlViewModel.kt` and `ui/screens/SettingsScreen.kt`
      to the new `BundledModelManager` API (`languagePacks`/`verifiedAssets`/
      `isManifestLoaded` instead of the old fake `bundledModels`/`benchmarkResults`).
      The Settings "Test" button now triggers a real model reload + real measured
      load-time (`IndicSttEngine.reloadAndBenchmark`), not fake byte arithmetic.
- [x] Fixed misleading default values in `SttModelInfo`/`TtsModelInfo` (removed
      "TFLite"/"IndicWav2Vec"/"FastSpeech2" claims that were never true).
- Still TODO on the STT side: nothing functionally — Hindi STT is real end-to-end.
  English STT still needs its own IndicConformer conversion (trysem's repo doesn't
  have an `en` folder — need a different source or accept English via a different
  route; not blocking, since Hindi is the flagship pilot language per the plan).

## In progress / not yet done

- [ ] TTS checkpoint downloads (AI4Bharat's own official `Indic-TTS` FastPitch+HiFiGAN
      release zips, MIT-licensed): `hi.zip` and `en.zip` (~1.5GB each) into
      `C:\mlbuild\downloads\tts_hi\hi.zip` / `tts_en\en.zip`. These were ~65% done
      when the session was interrupted; resumed with `curl -C -` (resumable). Check
      file size vs. the real GitHub release size (hi.zip=1519.6MB, en.zip=1533.5MB)
      to confirm completion before unzipping.
- [ ] Once `hi.zip`/`en.zip` are complete: unzip, inspect `config.json` for each
      (fastpitch + hifigan) to confirm: character-vs-phoneme frontend, the exact
      `characters` vocab, sample rate, and the specific AI4Bharat/Coqui model class
      (repo uses a custom fork: `github.com/gokulkarthik/TTS` +
      `github.com/gokulkarthik/Trainer`, no pinned commit in their README — the
      checkpoint's own `config.json` is the authoritative source of truth once we
      have it, not the fork's current HEAD).
- [ ] **Architecture correction made after reading sherpa-onnx's actual Tts.kt**:
      sherpa-onnx's native TTS path only supports specific hardcoded model families
      (vits / matcha / kokoro / kitten / zipvoice / supertonic) whose C++ decode logic
      expects those exact architectures' tensor conventions. FastPitch+HiFiGAN doesn't
      fit any of those slots (Matcha looks superficially similar — acoustic model +
      vocoder — but is a flow-matching architecture with different I/O, not a generic
      "any 2-stage TTS" slot). **Decision: do NOT force FastPitch+HiFiGAN through
      sherpa-onnx's TTS API.** Instead:
      - STT + VAD: sherpa-onnx (nemo_ctc + real Silero VAD) — genuinely fits, keep.
      - TTS: add `com.microsoft.onnxruntime:onnxruntime-android:1.22.0` (official,
        Maven Central, MIT license) and write our own small Kotlin inference code
        that calls the two exported ONNX graphs (FastPitch text→mel, HiFi-GAN mel→wav)
        directly with tensor names/shapes we control end-to-end (we write both the
        Python export script and the Kotlin caller, so no black-box mismatch risk).
- [ ] Write `tools/model_conversion/export_tts.py` (FastPitch + HiFiGAN → ONNX,
      using AI4Bharat's `gokulkarthik/TTS` + `gokulkarthik/Trainer` forks to get
      matching model classes for loading the `.pth` checkpoints) once the checkpoint
      zips are downloaded and inspected.
- [ ] Replace fake engines in the app (`IndicSttEngine.kt`, `SileroVadDetector.kt`,
      `IndicTtsEngine.kt`, delete `BundledAcousticSynthesizer.kt`) per the plan file
      at `C:\Users\harsh.vardhan\.claude\plans\giggly-churning-bengio.md`.
- [ ] Wire the sherpa-onnx AAR + onnxruntime-android into
      `itantra/app/build.gradle.kts` (+ `noCompress` for onnx/bin assets).
- [ ] Rewrite `model_manifest.json` + `BundledModelManager.kt` with real data.
- [ ] Update `SettingsScreen.kt` / `StatusReadoutCard.kt` fake labels.
- [ ] Build APK (`./gradlew assembleDebug`) and test in the `Pixel_7_API_34` emulator.

## Update — TTS checkpoints downloaded, architecture confirmed real & standard

- [x] Both `hi.zip` and `en.zip` (AI4Bharat Indic-TTS official checkpoints) fully
      downloaded and `unzip -t` verified clean. Extracted to
      `C:\mlbuild\extracted\{hi,en}\`. Each contains `fastpitch/{best_model.pth,
      config.json, speakers.pth}` and `hifigan/{best_model.pth, config.json}`.
- [x] Inspected both config.json files directly (ground truth, not guessed):
  - **Text frontend is character-level, NOT phoneme-based** (`use_phonemes: false`,
    `phonemizer: null`, `text_cleaner: multilingual_cleaners`). This is much
    simpler than feared — no G2P/espeak needed at all, just direct character→id
    lookup, so the Kotlin-side "frontend" is trivial once we have the vocab list.
  - `characters_class: TTS.tts.models.vits.VitsCharacters` — a **standard Coqui
    TTS** class (not some AI4Bharat-only fork oddity). `num_chars: 92`
    (pad+bos+eos+blank+alphabet+punctuation, deduplicated/sorted by that class's
    own logic — some characters in the raw JSON look mojibake'd, which is exactly
    why we will NOT hand-parse this ourselves: we'll load it through the real
    library so whatever indices the model actually trained with are reproduced
    exactly, and dump the resulting id-ordered vocab list to `frontend.json` for
    the Kotlin tokenizer to do plain lookups against).
  - Model = `fast_pitch` / `base_model: forward_tts` (Coqui's `ForwardTTS` class),
    multi-speaker (`num_speakers: 2`, `speakers.pth` — AI4Bharat trained one male +
    one female voice per language; we'll default to speaker id 0).
  - Vocoder = standard `hifigan_generator` (HiFi-GAN V1: upsample factors
    [8,8,2,2], matches hop_length=256), mel: 80 bins, 22050 Hz, fmin 0/fmax 8000.
  - **Conclusion: this is 100% standard upstream Coqui-TTS architecture** (not a
    custom AI4Bharat model class), so ONNX export doesn't require AI4Bharat's old
    `gokulkarthik/TTS` fork at all — the actively-maintained `coqui-tts` PyPI
    package (idiap's continuation) should load these checkpoints directly.
- [x] Installed `torch==2.14.0+cpu` into `C:\mlbuild\venv` (CPU-only wheel, no CUDA
      needed for export-only work).
- [ ] NEXT STEP: `pip install coqui-tts` into the same venv, then write a Python
      script that: loads `en/fastpitch` + `en/hifigan` via the real library,
      extracts the actual trained character→id vocab + speaker embedding, traces
      `ForwardTTS.inference()` and `HifiganGenerator.inference()` through
      `torch.onnx.export` separately, INT8-quantizes both, and dumps
      `frontend.json` (vocab list + text_cleaner name + default speaker id). Do
      English first (simpler ASCII vocab, easier to sanity-check), then Hindi.
      This becomes `tools/model_conversion/export_tts.py` once proven working.

## Update — real PyTorch inference verified end-to-end; ONNX export in progress

- [x] **Major milestone**: got a real, working, end-to-end synthesis out of the
      actual AI4Bharat English checkpoint via the real `coqui-tts` library —
      `"This is an emergency alert, please evacuate immediately."` produced a
      4.03s WAV at 22050Hz with sane RMS/amplitude (not silence, not clipping).
      This is genuine proof the checkpoint + our understanding of its shapes are
      correct, before ever touching ONNX/Android.
  - Dependency install was a saga (`C:\mlbuild\venv`): `coqui-tts` 0.27.5 needs
    `transformers>=4.57,<5` specifically (5.x removed something it needs; way-old
    4.44 is missing something it needs — 4.57.6 is the sweet spot), plus
    `torchcodec` (required by torch>=2.9 for audio IO) and `torchaudio` (both CPU
    wheels from the pytorch cpu index). All now installed and working.
  - Both `fastpitch/config.json` files (en+hi) hardcode a **relative**
    `speakers_file: "models/v1/<lang>/fastpitch/speakers.pth"` path that only
    resolves if you `cd` to that exact spot — patched both configs in place to
    point at the real absolute path of each `speakers.pth` instead
    (`C:/mlbuild/extracted/<lang>/<lang>/fastpitch/speakers.pth`).
  - Confirmed shapes empirically (ground truth, not guessed): `tts_model` is a
    `TTS.tts.models.forward_tts.ForwardTTS`; `.inference()` returns
    `model_outputs` mel shaped **[1, T, 80] (time-first)**; the vocoder
    (`synth.vocoder_model.model_g`, a `HifiganGenerator`) wants mel
    **[1, 80, T] (channels-first)** — transpose between the two. Tokenizer
    (`TTS.tts.utils.text.tokenizer.TTSTokenizer`) is confirmed character-level;
    `tokenizer.characters.vocab` is the id-ordered list (92 entries for English)
    we dump straight into `frontend.json` for Kotlin to look up directly.
- [x] Wrote `C:\mlbuild\export_tts.py` (will become
      `tools/model_conversion/export_tts.py` once proven on both languages):
      wraps `ForwardTTS`/`HifiganGenerator` in thin `nn.Module`s, exports each
      via `torch.onnx.export(..., dynamo=False)` (torch 2.14's new default
      dynamo-based exporter needs `onnxscript`, which we don't have installed —
      forcing the legacy TorchScript-trace exporter avoids that dependency and
      is the well-trodden path for this architecture), verifies ONNX-vs-PyTorch
      numerical match on a reference sentence, INT8-quantizes both graphs, and
      writes `frontend.json` (vocab + text-cleaner name + speaker info).
  - Fixed along the way: `generator.remove_weight_norm()` throws under this
    torch version (parametrize API mismatch) — wrapped in try/except, it's a
    pure inference-speed nicety, not required for a correct export.
  - Currently re-running after the `dynamo=False` fix; check
    `C:\mlbuild\converted\en\tts\` for `fastpitch.int8.onnx`,
    `hifigan.int8.onnx`, `frontend.json` and the printed ONNX-vs-PyTorch diff
    numbers (should be small, e.g. <0.05) before trusting the export.
- Once English export is verified, run the identical command for Hindi
  (`C:/mlbuild/extracted/hi/hi hi C:/mlbuild/converted/hi/tts`) — same script,
  no code changes, since the checkpoint format is identical.

## Update — TTS is now REAL, verified, and wired into the app (major milestone)

- [x] **Solved the ONNX dynamic-shape export problem for FastPitch+HiFi-GAN.**
      Three real, understood bugs, each fixed properly (not worked around):
      1. `nn.MultiheadAttention`'s forward path bakes the traced sequence length
         into internal Reshape ops (a known PyTorch ONNX-export limitation) —
         replaced with `ONNXFriendlyMHA`, a hand-written equivalent using plain
         ops that stay dynamic under tracing.
      2. `TTS.tts.utils.helpers.sequence_mask()` builds its arange from a baked
         Python int in one call path — replaced with a version that's always
         dynamic (`onnx_friendly_sequence_mask`).
      3. Padding text to a fixed length (the "obvious" fix for a static ONNX
         input shape) turned out to be actively wrong: several layers in this
         architecture (FFTransformer's Conv1d feed-forward, the encoder overall)
         don't re-mask between sub-layers, so a padded position's own attention
         output leaks into real neighbors at the padding boundary — chased this
         through several partial fixes before realizing the real fix is to
         **never pad at all**: feed the exact real-length token sequence as a
         genuinely variable ONNX input (`dynamic_axes`), and build the mask with
         `torch.ones_like(input_ids)` (shape-dynamic by construction, no arange
         needed). Verified against live PyTorch on 5 sentences (5 to 103 chars):
         every one matches to <0.0001 max abs diff — not approximate, exact.
      - Also found and fixed: `format_durations()`'s "cast 0 durations to 1"
        clamp doesn't matter now (no padding exists to clamp).
      - HiFi-GAN itself (pure Conv1d/ConvTranspose1d) was never part of the bug —
        it exports with correct dynamic length out of the box. It's shipped as
        **fp32, not INT8** — dynamic quantization measurably hurt its output at
        unseen lengths (~0.43 max abs diff on a [-1,1] signal, real degradation,
        not noise), so INT8 was not worth it there specifically.
- [x] Real per-language TTS footprint (measured, not estimated):
      **fastpitch.int8.onnx ~63MB + hifigan.onnx (fp32) ~56MB ≈ 118MB/language.**
- [x] `tools/model_conversion/export_tts.py` is now the real, final, documented
      version of this pipeline (the file itself explains all three bugs inline).
- [x] Ran it for real on **English and Hindi** — both verified clean (max diff
      ~0.00002–0.00005 across multiple test sentences of very different lengths).
      Copied `fastpitch.int8.onnx` + `hifigan.onnx` + `frontend.json` into
      `app/src/main/assets/models/{en,hi}/tts/`.
- [x] Rewrote `tts/IndicTtsEngine.kt` for real: loads the two ONNX sessions via
      `onnxruntime-android`, tokenizes text using the real dumped vocab +
      `multilingual_cleaners`-equivalent normalization (NFC, lowercase, a few
      punctuation substitutions, whitespace collapse — replicated exactly from
      Coqui's `cleaners.py`, not guessed), runs FastPitch → transpose → HiFi-GAN
      → real 16-bit PCM → real `AudioTrack` playback. Deleted
      `BundledAcousticSynthesizer.kt` (the fake formant-buzzer fallback) entirely
      — there is no more fake fallback path.
- [x] `model_manifest.json` now has real `tts` entries for `hi` (stt+tts) and
      `en` (tts only — no AI4Bharat English STT export exists yet, see below).
- [x] Removed the now-unused `<queries>` block from AndroidManifest.xml
      (`RecognitionService`/`TTS_SERVICE` — neither Android system service is
      used anywhere anymore).

## Scope update — user wants all 10 SIH-mandated languages, not just the pilot

Currently converting the remaining 8 languages (Bengali, Marathi, Telugu, Tamil,
Gujarati, Kannada, Malayalam, Odia) in the background using the exact same two
scripts (`convert_stt.py`, `export_tts.py`) — no new engineering, just re-running
proven code. Two sequential bulk-download loops running concurrently:
- `C:\mlbuild\download_all_stt.sh` → `download_all_stt.log` (HuggingFace, ~470MB/lang)
- `C:\mlbuild\download_all_tts.sh` → `download_all_tts.log` (GitHub, ~1.5GB/lang)

Bandwidth is heavily contended (multiple large concurrent downloads this
session) — expect this to take a while; check the two log files for progress
(`grep DOWNLOADED` in each) and just re-run `convert_stt.py`/`export_tts.py` per
language as its raw files land, then copy the outputs into
`assets/models/<lang>/{stt,tts}/` and add a manifest entry (same shape as the
`hi`/`en` entries already there) — fully mechanical at this point.

**English has no AI4Bharat STT export available** (the `trysem/indicconformer-120m-onnx`
source repo covers hi/gu/mr/kn/ml/ta/te/or/bn but not `en`) — flagged as a known
gap, not silently ignored. Options if this needs closing later: check whether
`ai4bharat/indic-conformer-600m-multilingual` (the larger multilingual NeMo
checkpoint) includes English and export that specifically, or accept English as
TTS-only in this submission (English STT isn't the primary ask for an Indian
disaster-response app; Hindi + the 8 regional languages are the real target).

## Interruption note (for whoever/whatever resumes this)

This session's process was killed/restarted once already mid-work (three background
shell tasks — two curl downloads, one gradle build — were found "stopped" with no
completion record on resume). Nothing was lost because: (a) completed work products
were already flushed to disk (STT conversion output, cloned repos, extracted native
libs), and (b) in-flight downloads are resumable (`curl -C -`) and the AAR build is
cheap to just re-run. **If this happens again**: check file sizes in
`C:\mlbuild\downloads\` against the expected sizes noted above before assuming a
download is complete, and re-run rather than trust a task that isn't confirmed
"completed" in a notification.

## Session update — all 10 languages done, crash fixed, mesh relay + RAM work

### All 10 SIH languages now shipped
STT: hi, bn, gu, kn, ml, mr, ta, te, or (9/10 — `en` still has no AI4Bharat STT
export, see note above, unchanged). TTS: all 10 (hi, en, bn, gu, kn, ml, mr, ta, te,
or). `model_manifest.json` has a complete entry for every language. Odia's STT
download initially landed at `C:\c\mlbuild\...` (same bash-`/c/...`-path-on-native-
Windows-Python bug as before) — moved into place and converted normally. Every TTS
checkpoint's `fastpitch/config.json` ships with a *training-machine-relative*
`speakers_file` path (`models/v1/<lang>/fastpitch/speakers.pth`) baked into **two**
places in the JSON (a top-level key and a duplicate under `model_args`) — must patch
both to the real absolute path before `export_tts.py` will load the checkpoint, or
Coqui's `SpeakerManager` throws `FileNotFoundError`. Do this for any future language
re-conversion.

### Real crash root-caused and fixed: onnxruntime symbol version mismatch
The `System.loadLibrary("onnxruntime")`-before-sherpa-onnx fix from the previous
session was treating the wrong theory and did **not** actually fix the launch crash
(same `UnsatisfiedLinkError: cannot locate symbol "OrtGetApiBase"` recurred on
rebuild). Root cause, found via `llvm-readelf --dyn-syms`/`-d` on the extracted
`.so`s: `libsherpa-onnx-jni.so` (from the sherpa-onnx v1.13.7 prebuilt AAR) has an
undefined **versioned** symbol `OrtGetApiBase@VERS_1.27.1`, but the
`onnxruntime-android` Maven dependency we use for our own TTS ONNX sessions is
1.22.0, whose `libonnxruntime.so` only defines `OrtGetApiBase@@VERS_1.22.0`. Android's
bionic linker requires an *exact* version-string match for versioned symbol
resolution — no amount of load-ordering or preloading fixes a genuine version
mismatch. 1.27.1 isn't published to Maven Central (nearest: 1.27.0/1.28.0/1.29.0,
still a mismatched string) and rebuilding sherpa-onnx-jni.so from source against
1.22.0 would mean a real NDK/CMake cross-compile.

**Actual fix**: k2-fsa publishes a `sherpa-onnx-v1.13.7-android-static-link-onnxruntime.tar.bz2`
release asset — its `libsherpa-onnx-jni.so` has onnxruntime statically linked in
(no `NEEDED libonnxruntime.so` entry, no exported/undefined `OrtGetApiBase` symbol at
all) for arm64-v8a/armeabi-v7a/x86_64 (x86 still needs a separate `libonnxruntime.so`,
so x86 was dropped from the AAR — real ARM/x86_64 phones and emulators are unaffected).
Patched `app/libs/sherpa-onnx-1.13.7.aar` by swapping in these statically-linked
`.so`s per ABI (original dynamic-link AAR backed up as `sherpa-onnx-1.13.7.aar.dynamic-link.bak`
next to it). This fully isolates sherpa-onnx's onnxruntime copy from the separate
onnxruntime-android 1.22.0 copy our TTS code uses — no more shared-symbol version
requirement between them at all. Verified via `adb logcat`: real STT+VAD loads
cleanly on the physical device (`IndicSttEngine: Loaded real STT+VAD for 'hi' in
4557ms`), no FATAL/UnsatisfiedLinkError.

### RAM/CPU optimization for 2GB-RAM low-end devices (explicit user ask)
- `IndicSttEngine`/`IndicTtsEngine` now extract their model files from
  `assets/` to internal storage once (`context.filesDir/models_cache/...`, size-
  checked against `BundledModelManager`'s already-computed `sizeBytes` so re-launches
  don't re-copy) and load sherpa-onnx/ONNX Runtime sessions **by file path**
  (`assetManager = null` / `Vad`+`OfflineRecognizer`'s `newFromFile` path,
  `ortEnv.createSession(path, ...)`) instead of reading the whole multi-hundred-MB
  model into a JVM `byte[]` first. Avoids holding both a JVM-heap copy and ONNX
  Runtime's own parsed copy resident at once.
- STT recognizer thread count 2 → 1; TTS `SessionOptions` explicitly set to 1
  intra-op/1 inter-op thread + memory pattern optimization on — low-end SoCs don't
  have threads to spare and extra ORT workers mainly buy scratch-buffer RAM, not
  useful latency here.
- **Real bug found & fixed**: `MissionControlViewModel` constructed `IndicSttEngine`
  and `IndicTtsEngine` each with their own default `BundledModelManager(context)`
  instead of sharing one — so on every app launch, ~2GB of bundled model files got
  SHA-256-verified **twice, in parallel**, competing for the same CPU cores. Now both
  engines share one `BundledModelManager` instance (constructed once in the
  ViewModel and passed to both). Additionally, `BundledModelManager.verifyAsset()`
  now runs concurrently across all referenced files (`coroutineScope { toVerify.map
  { async { ... } } }`) instead of one file at a time, and
  `loadAndVerifyBundledModels()` itself is now idempotent (`AtomicBoolean` +
  `CompletableDeferred` guard) so calling it from both engines' `init{}` blocks is a
  cheap no-op the second time. Net effect verified on-device: manifest+hash
  verification for all 10 languages (49 assets) now completes in ~20s total (one
  pass) instead of stalling for minutes (two duplicate passes racing each other).

### Settings screen "Test" button was silently broken (real bug, user-reported)
`SttEngine.modelInfo` / `TtsEngine.modelInfo` were plain `var`/`val` properties, not
`StateFlow`. `MissionControlViewModel` read them **once** at construction
(`val sttModelInfo: SttModelInfo = sttEngine.modelInfo`) and never again, so even
though pressing "Test" in Settings genuinely re-ran `IndicSttEngine.reloadAndBenchmark()`
(real model reload, real latency measured), the UI had no way to ever see the
updated value — nothing on screen ever changed. Separately, there wasn't even a UI
element displaying `inferenceLatencyMs` anywhere. Fixed: both interfaces now expose
`StateFlow<SttModelInfo>`/`StateFlow<TtsModelInfo>` (backed by `MutableStateFlow` in
the engines, every `modelInfo = ...`/`.copy(...)` call site updated to
`_modelInfo.value = ...`), `MissionControlViewModel` exposes them as `StateFlow`
instead of a one-time snapshot, and `SettingsScreen` now renders a live "STT: <name>
· <ms>" / "TTS: <name>" status line above the language list that updates the moment
a Test benchmark completes.

### Mesh relay (new feature, explicit user ask)
Previous transport (`TacticalMeshTransport`) only ever held **one** active TCP or
Bluetooth socket at a time (`activeTcpClientSocket`/`activeBluetoothSocket` were
singular fields, overwritten on each new connection) — genuinely single-peer,
walkie-talkie-pair-only, no multi-hop relay possible even though UDP beacon/discovery
already tracked multiple peers. Rebuilt around a `peerLinks: ConcurrentHashMap<String,
PeerLink>` (keyed by remote IP for TCP/Wi-Fi-Direct links, by Bluetooth MAC for BT
links) so a node can hold several simultaneous links at once — the actual
prerequisite for "B relays between distant A and C". Added:
- `NetworkPacket.ttl` (default 6) + `.relayHops` fields.
- `TransportLayer.connectedPeers: StateFlow<List<PeerDevice>>` (new, alongside the
  existing single `connectedPeer` kept for UI backward-compat — it now resolves to
  the strongest-signal entry of `connectedPeers`) and `disconnectPeer(peerKey)`.
- Flood-relay with dedup: `seenPacketIds: ConcurrentHashMap<String, Long>` (packetId
  → first-seen time, pruned every 5s past 60s old) makes "have I already
  processed/relayed this packet" an atomic `putIfAbsent` check — a node delivers a
  packet locally exactly once and, if `ttl > 1`, decrements ttl and rewrites it out
  to every *other* live link (`relayToMeshPeers(json, excludeKey = sourceAddress)`),
  strongest-signal-first (pure QoS ordering, not required for correctness — every
  remaining link still gets written to). This is what actually implements "A and C
  both only reach B, B relays between them" — no manual routing table needed, flood-
  with-TTL-and-dedup is sufction for a small ad-hoc mesh.
  - `startPeerPruning()`'s existing 5s tick now also drops any `peerLinks` entry
    whose underlying `Socket`/`BluetoothSocket` has actually died, so a stale link
    doesn't sit there absorbing relay writes silently.
- `PairingScreen` now shows a "MESH RELAY ACTIVE · bridging N peers" card (signal-
  sorted) whenever `connectedPeers.size > 1`, so the demo has something to visibly
  point at.
- **Not yet exercised end-to-end on two physical devices** (only one phone was
  connected this session; a second was expected but never enumerated by `adb
  devices` despite several checks — likely a USB/cable issue on the user's end, not
  a code issue). The single-device smoke test (build, install, launch, no crash) is
  clean; the actual multi-hop relay behavior (A–B–C chain) still needs a real 2-3
  phone test pass before calling it verified, not just "should work by construction."

### APK size — explicit tradeoff, user chose "bundle everything"
All 10 languages' STT+TTS models bundled directly in the base APK brought
`app-debug.apk` to **~2.3-2.4GB** (9 STT × ~141MB INT8 + 10 TTS × ~118MB
(62MB FastPitch INT8 + 55MB HiFi-GAN fp32) + native lib duplication from the
onnxruntime static-link fix above). Flagged to the user directly (Play-Store-style
per-language dynamic delivery was the alternative, but is real extra engineering and
would need `bundletool`/local sideload support since this isn't going through Play
Store for judging) — user explicitly chose "bundle everything, ignore size,
recommended for demo" since RAM/runtime efficiency (what's actually graded) is
already being optimized separately from on-disk/install size. Large `adb install`
transfers now routinely take 5-10+ minutes over USB; this is expected, not a hang.

### Still open / next steps
1. Two-physical-device mesh relay test (PTT on phone A → STT → mesh send → relayed
   through/received by phone B, and ideally a 3rd phone C to actually exercise the
   relay-not-just-direct-link path) — blocked on the second phone actually enumerating
   over adb.
2. English STT gap unchanged (TTS-only) — see note above for the multilingual-
   checkpoint option if this needs closing.
3. Consider (if time remains) collapsing the two onnxruntime copies (sherpa-onnx's
   statically-linked one + the separate onnxruntime-android 1.22.0 one for TTS) into
   one, to cut native-lib size and avoid double-loading the ORT runtime into RAM —
   would need a small custom JNI shim around sherpa-onnx's own bundled onnxruntime.so
   instead of depending on the official Java bindings AAR. Not started; current
   two-copy setup works correctly, this would be a pure efficiency follow-up.
