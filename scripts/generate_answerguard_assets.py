#!/usr/bin/env python3
"""Generate high-fidelity branding assets for AnswerGuard 2026."""

import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

# 2026 Tactical Palette
Emerald = (45, 212, 191)      # #2DD4BF - Active Primary
DeepNavy = (11, 16, 20)      # #0B1014 - Background
Surface = (24, 34, 41)       # #182229 - Secondary
White = (248, 250, 252)      # #F8FAFC - Text Primary
Muted = (182, 194, 204)      # #B6C2CC - Text Secondary

REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_METADATA = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US" / "images"

def draw_shield(draw, center, size, color):
    cx, cy = center
    r = size // 2
    # Simple shield polygon
    pts = [
        (cx, cy - r),           # Top
        (cx + r, cy - r * 0.6), # Top right
        (cx + r, cy + r * 0.2), # Mid right
        (cx, cy + r),           # Bottom
        (cx - r, cy + r * 0.2), # Mid left
        (cx - r, cy - r * 0.6), # Top left
    ]
    draw.polygon(pts, fill=color)

def draw_phone_icon(draw, center, size, color):
    cx, cy = center
    w, h = size * 0.6, size * 0.9
    x1, y1 = cx - w//2, cy - h//2
    x2, y2 = cx + w//2, cy + h//2
    # Phone body
    draw.rounded_rectangle((x1, y1, x2, y2), radius=size//10, outline=color, width=max(2, size//20))
    # Earpiece
    draw.line((cx - w//4, y1 + h//10, cx + w//4, y1 + h//10), fill=color, width=max(1, size//30))
    # Home button / Notch area
    draw.ellipse((cx - size//15, y2 - h//8, cx + size//15, y2 - h//20), outline=color, width=max(1, size//40))

def generate_icon():
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background Circle (Adaptive Base)
    margin = 20
    draw.ellipse((margin, margin, size-margin, size-margin), fill=DeepNavy)

    # Shield Outline
    draw_shield(draw, (size//2, size//2 + 10), size//2 - 60, Emerald)

    # Phone in center
    draw_phone_icon(draw, (size//2, size//2 + 5), size//3, DeepNavy)

    # AI/Gemini Sparkle/Checkmark
    # Draw a stylized checkmark
    checkmark_color = White
    ck_pts = [
        (size//2 - 40, size//2 + 10),
        (size//2 - 10, size//2 + 40),
        (size//2 + 50, size//2 - 30)
    ]
    draw.line(ck_pts, fill=checkmark_color, width=15, joint="round")

    output_path = ANDROID_METADATA / "icon.png"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(f"✓ Generated Launcher Icon: {output_path}")

def generate_feature_graphic():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), DeepNavy)
    draw = ImageDraw.Draw(img)

    # Decorative Gradient/Pattern
    for i in range(0, w, 40):
        draw.line((i, 0, i - 100, h), fill=(20, 30, 40), width=2)

    # Branding
    # Using default font since we can't guarantee custom fonts here
    try:
        font_main = ImageFont.truetype("/System/Library/Fonts/Supplemental/Avenir Next Bold.ttc", 80)
        font_sub = ImageFont.truetype("/System/Library/Fonts/Supplemental/Avenir Next.ttc", 30)
    except:
        font_main = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    draw.text((60, 180), "AnswerGuard", fill=Emerald, font=font_main)
    draw.text((60, 280), "AI-Powered Call Screening", fill=White, font=font_sub)
    draw.text((60, 320), "Private. On-Device. Gemini-Ready.", fill=Muted, font=font_sub)

    # Shield Graphic on the right
    draw_shield(draw, (w - 200, h // 2), 150, Emerald)
    draw_phone_icon(draw, (w - 200, h // 2), 100, DeepNavy)

    output_path = ANDROID_METADATA / "featureGraphic" / "feature.png"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(output_path, "PNG")
    print(f"✓ Generated Feature Graphic: {output_path}")

if __name__ == "__main__":
    generate_icon()
    generate_feature_graphic()
