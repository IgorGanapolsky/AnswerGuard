#!/usr/bin/env python3
"""
Stellar Brand Generator v2 (June 2026 Edition).
Produces industry-leading visual assets for AnswerGuard.
Features: Glassmorphism, Dynamic Gradients, and Outcome-First Typography.
"""

import os
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

# 2026 Premium Tactical Palette
COLORS = {
    "Emerald": (45, 212, 191),      # Core Active
    "DeepNavy": (11, 16, 20),       # Background
    "Void": (5, 8, 12),             # Deep Shadows
    "Highlight": (110, 231, 183),   # Text Primary
    "Muted": (100, 116, 139),       # Metadata
    "Accent": (16, 185, 129),       # Safe Zone
    "Warning": (244, 63, 94),       # Alert
}

# Fix root detection
REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_IMAGES = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US" / "images"

def _load_font(size: int, bold: bool = False):
    font_candidates = [
        "/System/Library/Fonts/Supplemental/Avenir Next Condensed Heavy.ttf" if bold else "/System/Library/Fonts/Supplemental/Avenir Next.ttc",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    for path in font_candidates:
        if os.path.exists(path):
            try: return ImageFont.truetype(path, size=size)
            except: continue
    return ImageFont.load_default()

def draw_glass_shield(draw, center, size, color):
    cx, cy = center
    r = size // 2
    pts = [
        (cx, cy - r),
        (cx + r * 0.8, cy - r * 0.6),
        (cx + r, cy),
        (cx + r * 0.8, cy + r * 0.8),
        (cx, cy + r),
        (cx - r * 0.8, cy + r * 0.8),
        (cx - r, cy),
        (cx - r * 0.8, cy - r * 0.6),
    ]
    for i in range(10, 0, -1):
        alpha = int(255 * (1 - i/15))
        draw.polygon(pts, fill=color + (alpha,), outline=COLORS["Highlight"] + (50,))
        pts = [(p[0], p[1] - 1) for p in pts]

def generate_stellar_icon():
    size = 1024
    img = Image.new("RGBA", (size, size), (0,0,0,0))
    draw = ImageDraw.Draw(img)
    draw.ellipse((20, 20, size-20, size-20), fill=COLORS["DeepNavy"])
    draw_glass_shield(draw, (size//2, size//2 + 50), size//2 - 100, COLORS["Emerald"])
    pw, ph = size//10, size//6
    draw.rounded_rectangle((size//2-pw, size//2-ph, size//2+pw, size//2+ph), radius=30, fill=COLORS["Void"])
    for i in range(0, 10):
        x = size//2 - 40 + i*10
        h = 10 + math.sin(i) * 30
        draw.line((x, size//2 - h, x, size//2 + h), fill=COLORS["Highlight"], width=4)

    icon_path = ANDROID_IMAGES / "icon.png"
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    img.resize((512, 512), Image.Resampling.LANCZOS).save(icon_path)
    print(f"🌟 Generated Stellar Icon: {icon_path}")

def generate_stellar_feature():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), COLORS["DeepNavy"])
    draw = ImageDraw.Draw(img)
    for x in range(0, w, 40):
        draw.line((x, 0, x - 100, h), fill=(20, 30, 40), width=1)
    f_h1 = _load_font(100, bold=True)
    f_h2 = _load_font(30)
    draw.text((60, 140), "AnswerGuard", fill=COLORS["Emerald"], font=f_h1)
    draw.text((65, 260), "AUTONOMOUS AI CALL SHIELD", fill=COLORS["Highlight"], font=f_h2)
    draw.text((65, 300), "JUNE 2026 SECURITY CORE", fill=COLORS["Muted"], font=f_h2)
    draw_glass_shield(draw, (w-200, h//2 + 20), 200, COLORS["Emerald"])
    path = ANDROID_IMAGES / "featureGraphic" / "feature.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"🌟 Generated Stellar Feature Graphic: {path}")

def generate_stellar_screenshots(device="phone", width=1080, height=2340):
    ss_dir = ANDROID_IMAGES / f"{device}Screenshots"
    ss_dir.mkdir(parents=True, exist_ok=True)
    screens = [
        ("01_AI", "AI INTENT ANALYSIS", "Gemini Nano decodes caller intent locally.", "GEMINI READY"),
        ("02_PRIVATE", "ZERO-KNOWLEDGE LOGS", "Call data stays in your hardware enclave.", "100% PRIVATE"),
        ("03_FRAUD", "DEEPFAKE DEFENSE", "Block AI voice clones in real-time.", "ANTI-FRAUD"),
        ("04_HOUSEHOLD", "FAMILY PROTECTION", "Tactical security for your entire home.", "FAMILY")
    ]
    for name, title, sub, badge in screens:
        img = Image.new("RGB", (width, height), COLORS["Void"])
        draw = ImageDraw.Draw(img)
        bw, bh = 400, 80
        draw.rounded_rectangle((width//2-bw//2, 100, width//2+bw//2, 180), radius=20, fill=COLORS["Emerald"])
        draw.text((width//2 - 100, 120), badge, fill=COLORS["Void"], font=_load_font(40, bold=True))
        f_h1 = _load_font(int(height * 0.04), bold=True)
        draw.text((width*0.1, height*0.12), title, fill=COLORS["Highlight"], font=f_h1)
        f_sub = _load_font(int(height * 0.018))
        draw.text((width*0.1, height*0.18), sub, fill=COLORS["Muted"], font=f_sub)
        draw.rounded_rectangle((width*0.08, height*0.25, width*0.92, height*0.92), radius=60, outline=COLORS["Emerald"], width=6)
        path = ss_dir / f"{name}.png"
        img.save(path)
        print(f"🌟 Generated {device} Screenshot: {path}")

def generate_release_notes():
    notes = """JUNE 2026 SECURITY CORE UPDATE:
- Autonomous AI Call Shield: Gemini Nano now analyzes unknown caller intent entirely on-device.
- Voice Deepfake Defense: New biometric layers to detect and block AI-cloned voices in real-time.
- Zero-Knowledge History: Activity logs are now 100% private and never leave your device.
- Household Sync: Pro/Family members can now share blocklists via private encrypted channels.
- Optimized for Android 17: Full support for the latest system-level call screening APIs."""
    path = ANDROID_IMAGES.parent / "changelogs" / "default.txt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(notes)
    print(f"✅ Generated Release Notes: {path}")

if __name__ == "__main__":
    generate_stellar_icon()
    generate_stellar_feature()
    generate_stellar_screenshots("phone", 1080, 2340)
    generate_stellar_screenshots("sevenInch", 1200, 1920)
    generate_stellar_screenshots("tenInch", 1600, 2560)
    generate_release_notes()
