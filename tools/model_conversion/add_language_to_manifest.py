#!/usr/bin/env python3
"""
Mechanical last step for adding a newly-converted language to the app: copies
the converted STT/TTS files into app/src/main/assets/models/<lang>/ and adds
(or updates) that language's entry in model_manifest.json.

Run this AFTER convert_stt.py and/or export_tts.py have produced their output
for a language — this script doesn't do any conversion itself, just wiring.

Usage:
    python add_language_to_manifest.py <lang_code> <english_name> \
        [--stt-dir <dir with model.int8.onnx + tokens.txt>] \
        [--tts-dir <dir with fastpitch.int8.onnx + hifigan.onnx + frontend.json>]

Example:
    python add_language_to_manifest.py bn Bengali \
        --stt-dir C:/mlbuild/converted/bn/stt --tts-dir C:/mlbuild/converted/bn/tts
"""
import argparse
import json
import shutil
from pathlib import Path

ASSETS_MODELS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "models"
MANIFEST_PATH = ASSETS_MODELS / "model_manifest.json"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("lang_code")
    ap.add_argument("english_name")
    ap.add_argument("--stt-dir")
    ap.add_argument("--tts-dir")
    ap.add_argument("--stt-source-model", default="")
    args = ap.parse_args()

    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    lang_entry = manifest["languages"].get(args.lang_code, {"englishName": args.english_name})
    lang_entry["englishName"] = args.english_name

    if args.stt_dir:
        src = Path(args.stt_dir)
        dest = ASSETS_MODELS / args.lang_code / "stt"
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src / "model.int8.onnx", dest / "model.int8.onnx")
        shutil.copy2(src / "tokens.txt", dest / "tokens.txt")
        vocab_size = sum(1 for _ in (src / "tokens.txt").open(encoding="utf-8"))
        lang_entry["stt"] = {
            "id": f"ai4bharat_indicconformer_{args.lang_code}",
            "name": "AI4Bharat IndicConformer (hybrid CTC/RNNT, CTC branch), INT8",
            "architecture": "Conformer-Large, 120M params",
            "sourceModel": args.stt_source_model
            or f"ai4bharat/indicconformer_stt_{args.lang_code}_hybrid_ctc_rnnt_large",
            "onnxExport": "trysem/indicconformer-120m-onnx (CC-BY-4.0)",
            "modelType": "nemo_ctc",
            "model": f"{args.lang_code}/stt/model.int8.onnx",
            "tokens": f"{args.lang_code}/stt/tokens.txt",
            "sampleRateHz": 16000,
            "featureDim": 80,
        }
        print(f"[{args.lang_code}] STT copied ({vocab_size} tokens) -> {dest}")

    if args.tts_dir:
        src = Path(args.tts_dir)
        dest = ASSETS_MODELS / args.lang_code / "tts"
        dest.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src / "fastpitch.int8.onnx", dest / "fastpitch.int8.onnx")
        shutil.copy2(src / "hifigan.onnx", dest / "hifigan.onnx")
        shutil.copy2(src / "frontend.json", dest / "frontend.json")
        lang_entry["tts"] = {
            "id": f"ai4bharat_indictts_{args.lang_code}",
            "name": "AI4Bharat Indic-TTS FastPitch (INT8) + HiFi-GAN V1",
            "sourceModel": f"AI4Bharat/Indic-TTS {args.lang_code}.zip (MIT)",
            "acoustic": f"{args.lang_code}/tts/fastpitch.int8.onnx",
            "vocoder": f"{args.lang_code}/tts/hifigan.onnx",
            "frontend": f"{args.lang_code}/tts/frontend.json",
            "sampleRateHz": 22050,
        }
        print(f"[{args.lang_code}] TTS copied -> {dest}")

    lang_entry.setdefault("stt", None)
    lang_entry.setdefault("tts", None)
    manifest["languages"][args.lang_code] = lang_entry
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[{args.lang_code}] Updated {MANIFEST_PATH}")


if __name__ == "__main__":
    main()
