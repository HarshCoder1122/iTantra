#!/usr/bin/env python3
"""
Converts a per-language AI4Bharat IndicConformer CTC ONNX export
(from the community re-export at huggingface.co/trysem/indicconformer-120m-onnx)
into a sherpa-onnx-ready "nemo_ctc" offline ASR model:

  1. Reads {lang}/vocab.json (a plain JSON list of BPE sub-word tokens).
  2. Writes tokens.txt in sherpa-onnx's "<token> <id>" format, appending the
     blank symbol "<blk>" at id == len(vocab) (matches how the ONNX export's
     CTC output layer was sized: vocab_size + 1 for blank).
  3. Stamps the ONNX file with the metadata sherpa-onnx's NeMo-CTC loader
     requires (vocab_size, normalize_type, subsampling_factor, model_type).
  4. Produces an INT8 dynamically-quantized copy (model.int8.onnx) so the
     ~470MB fp32 export shrinks to a size that's actually reasonable to ship
     on a low/mid-range phone.

Usage:
    python convert_stt.py <lang_code> <path/to/model.onnx> <path/to/vocab.json> <output_dir>

Example:
    python convert_stt.py hi downloads/stt_hi/hi/model.onnx downloads/stt_hi/hi/vocab.json \
        ../../app/src/main/assets/models/hi/stt
"""
import json
import sys
from pathlib import Path

import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic

# Conformer encoders subsample the input mel-spectrogram frames by this
# factor before the CTC output layer (Citrinet uses 8; Conformer uses 4).
# AI4Bharat's IndicConformer is a Conformer-Large hybrid CTC/RNNT model.
SUBSAMPLING_FACTOR = 4
MODEL_TYPE = "EncDecHybridRNNTCTCBPEModel"


def build_tokens_txt(vocab_path: Path, tokens_out: Path) -> int:
    vocab = json.loads(vocab_path.read_text(encoding="utf-8"))
    if not isinstance(vocab, list):
        raise ValueError(
            f"Expected {vocab_path} to be a JSON list of tokens, got {type(vocab)}"
        )

    with tokens_out.open("w", encoding="utf-8") as f:
        for i, token in enumerate(vocab):
            f.write(f"{token} {i}\n")
        blank_id = len(vocab)
        f.write(f"<blk> {blank_id}\n")

    return len(vocab)


def add_model_metadata(onnx_path: Path, vocab_size: int) -> None:
    model = onnx.load(str(onnx_path))
    meta = {
        "vocab_size": str(vocab_size + 1),  # +1 for the blank symbol
        "normalize_type": "per_feature",
        "subsampling_factor": str(SUBSAMPLING_FACTOR),
        "model_type": MODEL_TYPE,
        "version": "1",
        "model_author": "ai4bharat",
        "comment": "AI4Bharat IndicConformer (hybrid CTC/RNNT, CTC branch) - "
        "https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual",
    }
    for key, value in meta.items():
        entry = model.metadata_props.add()
        entry.key = key
        entry.value = value
    onnx.save(model, str(onnx_path))
    print(f"[metadata] wrote {meta} into {onnx_path}")


def quantize(onnx_path: Path, out_path: Path) -> None:
    quantize_dynamic(
        model_input=str(onnx_path),
        model_output=str(out_path),
        per_channel=True,
        weight_type=QuantType.QUInt8,
    )
    fp32_mb = onnx_path.stat().st_size / 1e6
    int8_mb = out_path.stat().st_size / 1e6
    print(f"[quantize] {onnx_path.name}: {fp32_mb:.1f}MB -> {out_path.name}: {int8_mb:.1f}MB")


def main() -> None:
    if len(sys.argv) != 5:
        print(__doc__)
        sys.exit(1)

    lang, model_in, vocab_in, out_dir = sys.argv[1:5]
    model_in = Path(model_in)
    vocab_in = Path(vocab_in)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    working_model = out_dir / "model.onnx"
    working_model.write_bytes(model_in.read_bytes())

    tokens_out = out_dir / "tokens.txt"
    vocab_size = build_tokens_txt(vocab_in, tokens_out)
    print(f"[{lang}] tokens.txt written with {vocab_size} tokens + 1 blank -> {tokens_out}")

    add_model_metadata(working_model, vocab_size)

    int8_out = out_dir / "model.int8.onnx"
    quantize(working_model, int8_out)

    # Only the quantized model + tokens.txt need to ship in the app; keep the
    # fp32 copy out of assets/ to save space once quantization is verified.
    print(f"[{lang}] done. Ship {int8_out.name} + {tokens_out.name} from {out_dir}")


if __name__ == "__main__":
    main()
