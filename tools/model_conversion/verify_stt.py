#!/usr/bin/env python3
"""
Loads a converted sherpa-onnx nemo_ctc model and runs it against a WAV file
(or, if no WAV is given, a short synthetic silence buffer just to confirm the
graph/metadata/tokens are wired correctly and inference doesn't crash).

Usage:
    python verify_stt.py <model_dir> [path/to/sample.wav]
"""
import sys
import wave
from pathlib import Path

import numpy as np
import sherpa_onnx


def load_wav_16k_mono(path: str):
    with wave.open(path, "rb") as wf:
        assert wf.getframerate() == 16000, f"expected 16kHz, got {wf.getframerate()}"
        assert wf.getnchannels() == 1, f"expected mono, got {wf.getnchannels()} channels"
        n = wf.getnframes()
        raw = wf.readframes(n)
    samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    return samples


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    model_dir = Path(sys.argv[1])
    model_path = model_dir / "model.int8.onnx"
    tokens_path = model_dir / "tokens.txt"

    print(f"Loading {model_path} ({model_path.stat().st_size / 1e6:.1f} MB) ...")
    recognizer = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=str(model_path),
        tokens=str(tokens_path),
        num_threads=2,
        sample_rate=16000,
        feature_dim=80,
        decoding_method="greedy_search",
    )
    print("Recognizer constructed OK (graph + metadata + tokens all consistent).")

    if len(sys.argv) >= 3:
        wav_path = sys.argv[2]
        samples = load_wav_16k_mono(wav_path)
        print(f"Loaded {wav_path}: {len(samples)/16000:.2f}s of audio")
    else:
        # 1 second of near-silence just to exercise the full forward pass.
        samples = (np.random.randn(16000) * 0.001).astype(np.float32)
        print("No WAV given; running on 1s synthetic near-silence to smoke-test the graph.")

    stream = recognizer.create_stream()
    stream.accept_waveform(16000, samples)
    recognizer.decode_stream(stream)
    text = stream.result.text
    print(f"RESULT TEXT: {text!r}")
    print("OK: model loaded and ran end-to-end without error.")


if __name__ == "__main__":
    main()
