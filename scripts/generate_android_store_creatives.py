#!/usr/bin/env python3
"""Generate high-fidelity, outcome-first Android store screenshots for 2026."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
from typing import Dict, Tuple

# 2026 Tactical Palette
Emerald = (45, 212, 191)      # #2DD4BF - Active Primary
DeepNavy = (11, 16, 20)      # #0B1014 - Background
Surface = (24, 34, 41)       # #182229 - Secondary
White = (248, 250, 252)      # #F8FAFC - Text Primary
Muted = (182, 194, 204)      # #B6C2CC - Text Secondary
TacticalRed = (220, 38, 38)  # Badge Red

REPO_ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def _load_font(size: int, bold: bool = False):
    font_candidates = [
        "/System/Library/Fonts/Supplemental/Avenir Next Condensed Heavy.ttf" if bold else "/System/Library/Fonts/Supplemental/Avenir Next.ttc",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    for path in font_candidates:
        if os.path.exists(path):
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()

def render_screenshot(filename: str, title: str, subtitle: str, badge: str):
    w, h = 1080, 2340
    base = Image.new("RGB", (w, h), DeepNavy)
    draw = ImageDraw.Draw(base)

    # Decorative Pattern
    for i in range(0, w, 60):
        draw.line((i, 0, i - 200, h), fill=(20, 30, 40), width=1)

    # Header Section
    title_font = _load_font(100, bold=True)
    subtitle_font = _load_font(45, bold=False)

    # Title
    t_bbox = draw.textbbox((0, 0), title, font=title_font)
    draw.text(((w - (t_bbox[2]-t_bbox[0])) // 2, 150), title, font=title_font, fill=White)

    # Subtitle
    s_bbox = draw.textbbox((0, 0), subtitle, font=subtitle_font)
    draw.text(((w - (s_bbox[2]-s_bbox[0])) // 2, 280), subtitle, font=subtitle_font, fill=Muted)

    # Result Badge
    badge_font = _load_font(40, bold=True)
    b_bbox = draw.textbbox((0, 0), badge, font=badge_font)
    bw, bh = b_bbox[2]-b_bbox[0], b_bbox[3]-b_bbox[1]
    bx1 = (w - (bw + 60)) // 2
    by1 = 380
    bx2, by2 = bx1 + bw + 60, by1 + bh + 30
    draw.rounded_rectangle((bx1, by1, bx2, by2), radius=10, fill=TacticalRed)
    draw.text((bx1 + 30, by1 + 10), badge, font=badge_font, fill=White)

    # Placeholder for App UI (since we are generating these purely from script for now)
    # in a real GSD we would paste a real screenshot here.
    ui_margin = 80
    ui_top = 500
    draw.rounded_rectangle((ui_margin, ui_top, w-ui_margin, h-ui_margin), radius=40, fill=Surface, outline=(40, 60, 70), width=4)

    # Inner "Active" Status Mock
    draw.ellipse((w//2-100, ui_top+150, w//2+100, ui_top+350), fill=Emerald)
    draw.text((w//2-150, ui_top+400), "SHIELD ACTIVE", fill=Emerald, font=_load_font(50, bold=True))

    output_path = OUTPUT_DIR / filename
    base.save(output_path, "PNG", optimize=True)
    print(f"✓ Generated Screenshot: {output_path}")

import os
if __name__ == "__main__":
    render_screenshot(
        "01-home.png",
        "AI CALL SCREENING",
        "Gemini Nano intent analysis on-device.",
        "100% PRIVATE"
    )
    render_screenshot(
        "02-how-it-works.png",
        "REAL-TIME VERDICTS",
        "See exactly why calls are blocked.",
        "ZERO UI"
    )
    render_screenshot(
        "03-pro.png",
        "FAMILY PROTECTION",
        "Secure your whole household with AI.",
        "2026 READY"
    )
