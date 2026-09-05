"""Wraps raw phone screenshots in a premium phone-bezel frame for the README.

GitHub's markdown renderer strips CSS, so a "phone mockup" has to be a flattened
image with the bezel already baked in rather than drawn with HTML/CSS. This is
100% original vector-style artwork drawn with Pillow -- deliberately not a
downloaded photorealistic device render, since none of the free ones we found
(e.g. community "device frame" datasets scraping real iPhone/Pixel product
photography) ship with a clear license to redistribute in another public repo.

Usage:
    python compose_mockup.py

Reads every *.png in docs/screenshots/raw/ and writes a framed version with the
same filename to docs/screenshots/ (which is what README.md actually embeds).
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "docs" / "screenshots" / "raw"
OUT_DIR = ROOT / "docs" / "screenshots"

BEZEL = 16                 # metallic edge thickness around the screen
CORNER_RADIUS = 64         # outer frame corner radius (modern-phone-ish)
SCREEN_RADIUS = 52         # inner screenshot corner radius
CAMERA_RADIUS = 9          # punch-hole selfie camera
SHADOW_BLUR = 36
SHADOW_MARGIN = 60
SUPERSAMPLE = 3            # render big, downsample for anti-aliased edges

BEZEL_LIGHT = (72, 74, 82, 255)
BEZEL_DARK = (14, 15, 18, 255)
SCREEN_RING = (46, 48, 56, 255)      # thin inner ring between bezel and screen
CAMERA_COLOR = (4, 4, 6, 255)
CAMERA_GLINT = (60, 70, 90, 200)
BUTTON_COLOR = (54, 56, 64, 255)
SHADOW_COLOR = (0, 0, 0, 110)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([(0, 0), (size[0] - 1, size[1] - 1)], radius=radius, fill=255)
    return mask


def metallic_gradient(size: tuple[int, int]) -> Image.Image:
    """Diagonal light-to-dark sweep so the edge reads as brushed metal, not flat paint."""
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w]
    t = (xx + yy) / float(w + h)
    v = 0.5 + 0.5 * np.sin(t * np.pi * 1.6 - 0.6)
    arr = np.clip(v * 255, 0, 255).astype(np.uint8)
    grad = Image.fromarray(arr, mode="L")
    return grad.filter(ImageFilter.GaussianBlur(w * 0.03))


def compose_one(src_path: Path, dst_path: Path) -> None:
    shot = Image.open(src_path).convert("RGBA")
    w, h = shot.size
    S = SUPERSAMPLE

    bezel = BEZEL * S
    corner_r = CORNER_RADIUS * S
    screen_r = SCREEN_RADIUS * S
    ring = max(1, 3 * S)

    frame_w, frame_h = (w + bezel * 2) * 1, (h + bezel * 2) * 1
    shot_big = shot.resize((w * S, h * S), Image.LANCZOS)
    fw, fh = w * S + bezel * 2, h * S + bezel * 2

    canvas_w = fw + SHADOW_MARGIN * S * 2
    canvas_h = fh + SHADOW_MARGIN * S * 2
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))

    # --- Soft drop shadow ---
    shadow = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [
            (SHADOW_MARGIN * S, SHADOW_MARGIN * S + 14 * S),
            (SHADOW_MARGIN * S + fw, SHADOW_MARGIN * S + fh + 14 * S),
        ],
        radius=corner_r,
        fill=SHADOW_COLOR,
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR * S / 2))
    canvas = Image.alpha_composite(canvas, shadow)

    # --- Metallic bezel body (gradient, not flat) ---
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    frame_mask = rounded_mask((fw, fh), corner_r)
    grad = metallic_gradient((fw, fh))
    dark_layer = Image.new("RGBA", (fw, fh), BEZEL_DARK)
    light_layer = Image.new("RGBA", (fw, fh), BEZEL_LIGHT)
    metal = Image.composite(light_layer, dark_layer, grad)
    frame.paste(metal, (0, 0), frame_mask)

    # Thin inner ring (subtle contrast line separating bezel from glass).
    ring_rect_mask = Image.new("L", (fw, fh), 0)
    ring_draw = ImageDraw.Draw(ring_rect_mask)
    ring_draw.rounded_rectangle(
        [(bezel - ring, bezel - ring), (fw - bezel + ring - 1, fh - bezel + ring - 1)],
        radius=screen_r + ring,
        fill=255,
    )
    ring_draw.rounded_rectangle(
        [(bezel, bezel), (fw - bezel - 1, fh - bezel - 1)], radius=screen_r, fill=0
    )
    ring_layer = Image.new("RGBA", (fw, fh), SCREEN_RING)
    frame.paste(ring_layer, (0, 0), ring_rect_mask)

    # --- Screenshot inset with rounded corners ---
    shot_masked = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    shot_masked.paste(shot_big, (0, 0), rounded_mask((w * S, h * S), screen_r))
    frame.paste(shot_masked, (bezel, bezel), shot_masked)

    # --- Subtle glass highlight streak across the top third of the screen ---
    highlight = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    hl_draw = ImageDraw.Draw(highlight)
    band_w = int(w * S * 0.55)
    hl_draw.polygon(
        [
            (0, 0),
            (band_w, 0),
            (int(band_w * 0.35), int(h * S * 0.42)),
            (0, int(h * S * 0.42)),
        ],
        fill=(255, 255, 255, 22),
    )
    highlight = highlight.filter(ImageFilter.GaussianBlur(w * S * 0.02))
    highlight_masked = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))
    highlight_masked.paste(highlight, (0, 0), rounded_mask((w * S, h * S), screen_r))
    frame.alpha_composite(highlight_masked, (bezel, bezel))

    # --- Punch-hole selfie camera ---
    cam_cx, cam_r = fw // 2, CAMERA_RADIUS * S
    cam_cy = bezel // 2 + 4 * S
    cam_draw = ImageDraw.Draw(frame)
    cam_draw.ellipse(
        [(cam_cx - cam_r, cam_cy - cam_r), (cam_cx + cam_r, cam_cy + cam_r)], fill=CAMERA_COLOR
    )
    glint_r = max(1, cam_r // 3)
    cam_draw.ellipse(
        [
            (cam_cx - glint_r - cam_r * 0.3, cam_cy - glint_r - cam_r * 0.3),
            (cam_cx + glint_r - cam_r * 0.3, cam_cy + glint_r - cam_r * 0.3),
        ],
        fill=CAMERA_GLINT,
    )

    # --- Side buttons (power + volume rocker), right edge ---
    btn_w = max(2, int(2.5 * S))
    def side_button(y0_frac: float, y1_frac: float) -> None:
        cam_draw.rounded_rectangle(
            [(fw - btn_w // 2, int(fh * y0_frac)), (fw + btn_w, int(fh * y1_frac))],
            radius=btn_w,
            fill=BUTTON_COLOR,
        )
    side_button(0.14, 0.22)   # power
    side_button(0.27, 0.33)   # volume up
    side_button(0.34, 0.40)   # volume down

    canvas.alpha_composite(frame, (SHADOW_MARGIN * S, SHADOW_MARGIN * S))
    canvas = canvas.resize((canvas_w // S, canvas_h // S), Image.LANCZOS)
    canvas.save(dst_path)
    print(f"wrote {dst_path.relative_to(ROOT)} ({canvas.size[0]}x{canvas.size[1]})")


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
