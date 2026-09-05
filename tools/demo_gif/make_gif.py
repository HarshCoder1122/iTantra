"""Converts docs/demo.mp4 into a looping docs/demo.gif for the README.

GitHub's markdown renderer only truly embeds inline, autoplaying video when
it's uploaded through github.com's own web editor (drag-and-drop), which
produces a github.com/user-attachments/... URL. A plain committed .mp4
referenced via <video src="..."> does NOT reliably render as a playable
player. A GIF, on the other hand, is just an image as far as GitHub's
renderer is concerned -- it always shows up inline and autoplays. Trade-off:
no audio, larger-for-the-same-length file than a real video codec.

Usage:
    python make_gif.py
"""

from pathlib import Path

import imageio.v3 as iio
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "docs" / "demo.mp4"
DST = ROOT / "docs" / "demo.gif"

TARGET_WIDTH = 480     # downscale from source (typically 720p+) to keep file size sane
TARGET_FPS = 10        # source is commonly 24-30fps; a fraction of that is imperceptible in a GIF
MAX_COLORS = 96        # GIF palette size — enough for a UI-heavy screen recording


def main() -> None:
    meta = iio.immeta(SRC, plugin="pyav")
    src_fps = float(meta.get("fps", 24.0))
    stride = max(1, round(src_fps / TARGET_FPS))

    frames = []
    for i, frame in enumerate(iio.imiter(SRC, plugin="pyav")):
        if i % stride != 0:
            continue
        img = Image.fromarray(frame)
        w, h = img.size
        new_h = round(h * (TARGET_WIDTH / w))
        img = img.resize((TARGET_WIDTH, new_h), Image.LANCZOS)
        frames.append(img)

    if not frames:
        raise SystemExit("no frames decoded from source video")

    # Quantize every frame against one shared adaptive palette (built from the
    # first frame) so colors stay stable across frames instead of flickering.
    base_palette = frames[0].convert("P", palette=Image.ADAPTIVE, colors=MAX_COLORS)
    quantized = [f.quantize(palette=base_palette, dither=Image.FLOYDSTEINBERG) for f in frames]

    duration_ms = round(1000 / TARGET_FPS)
    quantized[0].save(
        DST,
        save_all=True,
        append_images=quantized[1:],
        duration=duration_ms,
        loop=0,
        optimize=True,
    )
    print(f"wrote {DST.relative_to(ROOT)}: {len(quantized)} frames @ {TARGET_FPS}fps, "
          f"{DST.stat().st_size / 1_000_000:.1f}MB")


if __name__ == "__main__":
    main()
