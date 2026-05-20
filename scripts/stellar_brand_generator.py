#!/usr/bin/env python3
"""Master Brand Generator for AnswerGuard June 2026.
Creates a unified visual identity across all Store Listing requirements.
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math

# 2026 Tactical Premium Palette
Emerald = (45, 212, 191)      # Primary Active
DeepNavy = (10, 15, 20)       # Base
Background = (5, 8, 12)       # Ultra Dark
Highlight = (60, 240, 220)    # Glow
TextPrimary = (248, 250, 252) # White
TextSecondary = (148, 163, 184)# Slate
WarningRed = (239, 68, 68)    # Alert

REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_IMAGES = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US" / "images"

def create_glow(draw, center, radius, color, alpha_fade=True):
    cx, cy = center
    for r in range(radius, 0, -5):
        alpha = int(100 * (1 - r/radius)) if alpha_fade else 50
        draw.ellipse((cx-r, cy-r, cx+r, cy+r), outline=color + (alpha,), width=2)

def draw_tactical_shield(draw, center, size, color, glow=False):
    cx, cy = center
    r = size // 2
    # Multi-layered tactical shield
    pts = [
        (cx, cy - r),
        (cx + r, cy - r * 0.5),
        (cx + r * 0.9, cy + r * 0.5),
        (cx, cy + r),
        (cx - r * 0.9, cy + r * 0.5),
        (cx - r, cy - r * 0.5),
    ]
    if glow:
        create_glow(draw, center, size + 20, Emerald)
    draw.polygon(pts, fill=color)
    # Inner Detail
    inner_pts = [(p[0], p[1] + (5 if p[1] < cy else -5)) for p in pts]
    draw.polygon(inner_pts, outline=Highlight + (100,), width=2)

def generate_launcher_icon():
    size = 1024 # High-res master
    img = Image.new("RGBA", (size, size), (0,0,0,0))
    draw = ImageDraw.Draw(img)

    # Adaptive Base Circle
    draw.ellipse((40, 40, size-40, size-40), fill=Background)

    # Glow effect
    draw_tactical_shield(draw, (size//2, size//2), size//2, (20, 30, 40, 255), glow=True)
    draw_tactical_shield(draw, (size//2, size//2), size//3, Emerald)

    # Phone silhouette in center
    pw, ph = size//6, size//4
    draw.rounded_rectangle((size//2-pw, size//2-ph, size//2+pw, size//2+ph), radius=30, fill=DeepNavy)
    draw.line((size//2-20, size//2-ph+20, size//2+20, size//2-ph+20), fill=Emerald, width=5) # Speaker

    # Gemini "Sparkle"
    draw.polygon([(size//2+80, size//2-80), (size//2+100, size//2-120), (size//2+120, size//2-80), (size//2+100, size//2-60)], fill=White)

    # Save versions
    icon_path = ANDROID_IMAGES / "icon.png"
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((512, 512), Image.Resampling.LANCZOS).save(icon_path)
    print(f"✅ Generated Stellar Icon: {icon_path}")

def generate_feature_graphic():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), Background)
    draw = ImageDraw.Draw(img)

    # Hex grid background pattern
    for x in range(0, w, 50):
        for y in range(0, h, 50):
            draw.regular_polygon((x, y, 20), 6, rotation=30, outline=(15, 25, 35))

    # Hero Content
    draw_tactical_shield(draw, (w-200, h//2), 220, Emerald, glow=True)

    # Copy
    draw.text((60, 150), "AnswerGuard", fill=Emerald, font_size=80)
    draw.text((60, 250), "AI AUTONOMOUS DEFENSE", fill=White, font_size=35)
    draw.text((60, 300), "JUNE 2026 SECURITY UPDATE", fill=Highlight, font_size=25)
    draw.text((60, 350), "100% On-Device intent Analysis", fill=TextSecondary, font_size=25)

    path = ANDROID_IMAGES / "featureGraphic" / "feature.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"✅ Generated Feature Graphic: {path}")

def generate_stellar_screenshots():
    ss_dir = ANDROID_IMAGES / "phoneScreenshots"
    ss_dir.mkdir(parents=True, exist_ok=True)

    screens = [
        ("01_SCREEN", "AI INTENT ANALYSIS", "Gemini Nano decodes caller intent in real-time.", "ULTRA PRIVATE"),
        ("02_THREAT", "VOICE CLONE DEFENSE", "Detect deepfake fraud before you answer.", "TACTICAL ALERT"),
        ("03_LOG", "TRANSPARENT VERDICTS", "Zero-knowledge activity logs kept on-device.", "ZERO LEAK"),
        ("04_FAMILY", "HOUSEHOLD SECURITY", "Multi-device protection for your family.", "FAMILY READY")
    ]

    w, h = 1080, 2340
    for i, (name, title, sub, badge) in enumerate(screens):
        img = Image.new("RGB", (w, h), Background)
        draw = ImageDraw.Draw(img)

        # High-tech background lines
        for j in range(0, w, 80):
            draw.line((j, 0, j - 400, h), fill=(10, 20, 30), width=1)

        # Top Badge
        draw.rounded_rectangle((w//2-200, 100, w//2+200, 180), radius=15, fill=WarningRed if "THREAT" in name else Emerald)
        draw.text((w//2-70, 115), badge, fill=White, font_size=35)

        # Title & Sub
        draw.text((80, 300), title, fill=White, font_size=85)
        draw.text((80, 420), sub, fill=TextSecondary, font_size=40)

        # UI Placeholder Frame
        draw.rounded_rectangle((100, 600, w-100, h-100), radius=50, fill=DeepNavy, outline=Emerald, width=5)

        # Abstract AI Waveform
        for x in range(200, w-200, 20):
            height = 50 + math.sin(x/50 + i) * 100
            draw.line((x, h//2-height, x, h//2+height), fill=Highlight, width=10)

        path = ss_dir / f"{name.lower()}.png"
        img.save(path)
        print(f"✅ Generated Stellar Screenshot: {path}")

if __name__ == "__main__":
    generate_launcher_icon()
    generate_feature_graphic()
    generate_stellar_screenshots()
