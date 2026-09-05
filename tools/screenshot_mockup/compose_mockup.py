"""Wraps raw phone screenshots in a simple flat phone-bezel frame for the README.

GitHub's markdown renderer strips CSS, so a "phone mockup" has to be a flattened
image with the bezel already baked in rather than drawn with HTML/CSS. This script
does that compositing locally with Pillow -- no external mockup web service.

Usage:
    python compose_mockup.py

Reads every *.png in docs/screenshots/raw/ and writes a framed version with the
same filename to docs/screenshots/ (which is what README.md actually embeds).
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "docs" / "screenshots" / "raw"
OUT_DIR = ROOT / "docs" / "screenshots"

BEZEL = 26          # bezel thickness around the screenshot
CORNER_RADIUS = 46  # outer frame corner radius
SCREEN_RADIUS = 30  # inner screenshot corner radius
NOTCH_W, NOTCH_H = 130, 26
SHADOW_BLUR = 24
SHADOW_MARGIN = 40
BEZEL_COLOR = (18, 18, 20, 255)
NOTCH_COLOR = (8, 8, 9, 255)
SHADOW_COLOR = (0, 0, 0, 90)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (size[0] - 1, size[1] - 1)], radius=radius, fill=255)
    return mask


def compose_one(src_path: Path, dst_path: Path) -> None:
    shot = Image.open(src_path).convert("RGBA")
    w, h = shot.size

    frame_w, frame_h = w + BEZEL * 2, h + BEZEL * 2
    canvas_w = frame_w + SHADOW_MARGIN * 2
    canvas_h = frame_h + SHADOW_MARGIN * 2

    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))

    # Soft drop shadow behind the frame.
    shadow = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        [
            (SHADOW_MARGIN, SHADOW_MARGIN + 10),
            (SHADOW_MARGIN + frame_w, SHADOW_MARGIN + frame_h + 10),
        ],
        radius=CORNER_RADIUS,
        fill=SHADOW_COLOR,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR))
    canvas = Image.alpha_composite(canvas, shadow)

    # Bezel.
    frame = Image.new("RGBA", (frame_w, frame_h), (0, 0, 0, 0))
    frame_draw = ImageDraw.Draw(frame)
    frame_draw.rounded_rectangle(
        [(0, 0), (frame_w - 1, frame_h - 1)], radius=CORNER_RADIUS, fill=BEZEL_COLOR
    )

    # Inset the screenshot with its own rounded mask.
    shot_masked = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    shot_masked.paste(shot, (0, 0), rounded_mask((w, h), SCREEN_RADIUS))
    frame.paste(shot_masked, (BEZEL, BEZEL), shot_masked)

    # Camera notch.
    notch_x0 = (frame_w - NOTCH_W) // 2
    frame_draw.rounded_rectangle(
        [(notch_x0, 8), (notch_x0 + NOTCH_W, 8 + NOTCH_H)],
        radius=NOTCH_H // 2,
        fill=NOTCH_COLOR,
    )

    # Side power button nub.
    frame_draw.rounded_rectangle(
        [(frame_w - 4, frame_h * 0.18), (frame_w + 4, frame_h * 0.28)],
        radius=4,
        fill=BEZEL_COLOR,
    )

    canvas.alpha_composite(frame, (SHADOW_MARGIN, SHADOW_MARGIN))
    canvas.save(dst_path)
    print(f"wrote {dst_path.relative_to(ROOT)} ({canvas_w}x{canvas_h})")


def main() -> None:
    if not RAW_DIR.exists():
        print(f"no raw screenshots found at {RAW_DIR}")
        return
    pngs = sorted(RAW_DIR.glob("*.png"))
    if not pngs:
        print(f"{RAW_DIR} is empty — drop raw screenshots there first")
        return
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for src in pngs:
        compose_one(src, OUT_DIR / src.name)


if __name__ == "__main__":
    main()
