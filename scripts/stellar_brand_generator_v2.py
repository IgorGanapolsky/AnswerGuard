#!/usr/bin/env python3
"""
Stellar Brand Generator v2 (June 2026 Edition).
Produces industry-leading visual assets for AnswerGuard.
Features: Glassmorphism, Dynamic Gradients, and Outcome-First Typography.
"""

import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

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

# 2026 Bold high-contrast brand icon palette (SOLID, no alpha/glass).
# Diagonal gradient between two analogous saturated greens/teals + pure white.
ICON_GRAD_TL = (21, 195, 154)       # #15C39A  top-left, bright teal-green
ICON_GRAD_BR = (10, 124, 90)        # #0A7C5A  bottom-right, deep emerald
ICON_WHITE = (255, 255, 255)        # #FFFFFF  shield silhouette

# Fix root detection
REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_IMAGES = REPO_ROOT / "native-android" / "fastlane" / "metadata" / "android" / "en-US" / "images"
IOS_IMAGES = REPO_ROOT / "native-ios" / "fastlane" / "screenshots" / "en-US"

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

def _lerp(a, b, t):
    """Linear interpolation between two RGB tuples."""
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _diagonal_gradient(size, top_left, bottom_right):
    """Build a fully-opaque RGB diagonal (TL->BR) gradient image."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    max_d = (size - 1) * 2.0
    for y in range(size):
        for x in range(size):
            t = (x + y) / max_d
            px[x, y] = _lerp(top_left, bottom_right, t)
    return img


def _shield_points(cx, cy, half_w, top_y, bottom_y):
    """Return a smooth, modern shield silhouette polygon (vector-like)."""
    import math
    top = top_y
    shoulder_y = top_y + (bottom_y - top_y) * 0.16
    mid_y = top_y + (bottom_y - top_y) * 0.52
    pts = [(cx, top), (cx + half_w, shoulder_y), (cx + half_w, mid_y)]
    # Right curve sweeping into the bottom tip.
    steps = 24
    for i in range(1, steps + 1):
        t = i / steps
        ang = t * (math.pi / 2)
        x = cx + half_w * math.cos(ang) * (1 - 0.10 * t)
        y = mid_y + (bottom_y - mid_y) * math.sin(ang)
        pts.append((x, y))
    # Mirror left side.
    right = pts[3:]
    for x, y in reversed(right):
        pts.append((cx - (x - cx), y))
    pts.append((cx - half_w, mid_y))
    pts.append((cx - half_w, shoulder_y))
    return pts


def draw_solid_shield(img, center, half_width, top_y, bottom_y, gradient_img):
    """Draw a SOLID white shield with a knocked-out gradient check mark.

    Uses a high-resolution supersampled mask so edges are crisp/anti-aliased
    (vector-like) rather than blurred. No alpha gradients, no glass.
    """
    ss = 4  # supersample factor
    w = img.width
    big = w * ss
    cx, cy = center[0] * ss, center[1] * ss
    pts = _shield_points(cx, cy, half_width * ss, top_y * ss, bottom_y * ss)

    # 1. Solid white shield silhouette.
    shield = Image.new("L", (big, big), 0)
    ImageDraw.Draw(shield).polygon(pts, fill=255)

    # 2. Bold check mark knocked out (drawn in gradient color over white).
    sw = int(round(0.105 * big))  # stroke width >= ~10.5% of icon width
    cw = half_width * ss
    ch = (bottom_y - top_y) * ss
    check = [
        (cx - cw * 0.46, cy + ch * 0.02),
        (cx - cw * 0.10, cy + ch * 0.30),
        (cx + cw * 0.52, cy - ch * 0.30),
    ]
    check_mask = Image.new("L", (big, big), 0)
    cdraw = ImageDraw.Draw(check_mask)
    cdraw.line(check, fill=255, width=sw, joint="curve")
    r = sw // 2
    for px_, py_ in (check[0], check[-1]):
        cdraw.ellipse((px_ - r, py_ - r, px_ + r, py_ + r), fill=255)

    # Compose at high res: gradient base -> white shield -> gradient check.
    base = gradient_img.resize((big, big), Image.Resampling.LANCZOS).convert("RGB")
    white_layer = Image.new("RGB", (big, big), ICON_WHITE)
    base.paste(white_layer, (0, 0), shield)
    # Knock the check out of the white shield by painting gradient back in,
    # but only where the shield exists (so the check never bleeds outside).
    from PIL import ImageChops
    check_in_shield = ImageChops.multiply(check_mask, shield)
    base.paste(gradient_img.resize((big, big), Image.Resampling.LANCZOS).convert("RGB"),
               (0, 0), check_in_shield)

    out = base.resize((w, w), Image.Resampling.LANCZOS)
    return out


def generate_stellar_icon():
    size = 1024
    # Fully-opaque diagonal gradient background (OS applies the rounded mask).
    grad = _diagonal_gradient(size, ICON_GRAD_TL, ICON_GRAD_BR)

    # Shield ~64% of canvas height, centered (slightly above center looks balanced).
    shield_h = int(size * 0.64)
    top_y = (size - shield_h) // 2
    bottom_y = top_y + shield_h
    half_w = int(size * 0.30)
    cx, cy = size // 2, (top_y + bottom_y) // 2

    composed = draw_solid_shield(grad, (cx, cy), half_w, top_y, bottom_y, grad)
    # Flatten to RGB to GUARANTEE no transparency anywhere.
    img = composed.convert("RGB")

    icon_path = ANDROID_IMAGES / "icon.png"
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    # Save a high-res 1024 source so iOS 1024 is downscaled (crisp), not upscaled.
    source_1024 = ANDROID_IMAGES / "icon-1024.png"
    img.save(source_1024)
    img.resize((512, 512), Image.Resampling.LANCZOS).save(icon_path)
    print(f"🌟 Generated Stellar Icon (solid 1024): {source_1024}")
    print(f"🌟 Generated Stellar Icon (512 source): {icon_path}")

def generate_stellar_feature():
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), COLORS["DeepNavy"])
    draw = ImageDraw.Draw(img)
    for x in range(0, w, 40):
        draw.line((x, 0, x - 100, h), fill=(20, 30, 40), width=1)
    f_h1 = _load_font(100, bold=True)
    f_h2 = _load_font(30)
    draw.text((60, 140), "AnswerGuard", fill=COLORS["Emerald"], font=f_h1)
    draw.text((65, 260), "PRIVATE SPAM CALL SHIELD", fill=COLORS["Highlight"], font=f_h2)
    draw.text((65, 300), "JUNE 2026 SECURITY CORE", fill=COLORS["Muted"], font=f_h2)
    # Solid brand shield on the right (no glass). Reuse the icon mark.
    mark_size = 360
    grad = _diagonal_gradient(mark_size, ICON_GRAD_TL, ICON_GRAD_BR)
    sh_h = int(mark_size * 0.64)
    sty = (mark_size - sh_h) // 2
    mark = draw_solid_shield(grad, (mark_size // 2, mark_size // 2),
                             int(mark_size * 0.30), sty, sty + sh_h, grad).convert("RGB")
    img.paste(mark, (w - mark_size - 60, (h - mark_size) // 2))
    path = ANDROID_IMAGES / "featureGraphic" / "feature.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"🌟 Generated Stellar Feature Graphic: {path}")

def generate_stellar_screenshots(target_dir, device="phone", width=1080, height=2340):
    target_dir.mkdir(parents=True, exist_ok=True)
    screens = [
        ("01_AI", "SCREENED CALLS", "See weekday, date, time, and handling reason.", "LOCAL"),
        ("02_PRIVATE", "PRIVATE BY DEFAULT", "Call logs and blocklists stay on your device.", "100% PRIVATE"),
        ("03_FRAUD", "BLOCK SPAM FAST", "Silence robocallers with local number screening.", "ANTI-SPAM"),
        ("04_HOUSEHOLD", "FAMILY PLAN", "Share through Google Play family sharing.", "FAMILY")
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
        path = target_dir / f"{name}.png"
        img.save(path)
        print(f"🌟 Generated {device} Screenshot: {path}")

def generate_release_notes():
    notes = """What's new in v1.2.7:
- Recent Activity now shows weekday, date, and time for screened calls.
- Carrier-routed voicemail and Do Not Disturb limitations are explained clearly.
- New launcher icon: shield + telephone handset.
- Delete my data clears screening history and blocklist without uninstalling.
- Refreshed privacy policy and store assets for the shipped local-screening feature set."""
    path = ANDROID_IMAGES.parent / "changelogs" / "default.txt"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(notes)
    print(f"✅ Generated Release Notes: {path}")

if __name__ == "__main__":
    generate_stellar_icon()
    generate_stellar_feature()

    # Android
    generate_stellar_screenshots(ANDROID_IMAGES / "phoneScreenshots", "phone", 1080, 2340)
    generate_stellar_screenshots(ANDROID_IMAGES / "sevenInchScreenshots", "sevenInch", 1200, 1920)
    generate_stellar_screenshots(ANDROID_IMAGES / "tenInchScreenshots", "tenInch", 1600, 2560)

    # iOS
    if IOS_IMAGES.parent.exists():
        generate_stellar_screenshots(IOS_IMAGES, "ios-phone", 1242, 2688)
        # Cleanup old iOS screenshots to ensure parity
        for old in ["01-home.png", "02-protection.png", "03-pro.png"]:
            p = IOS_IMAGES / old
            if p.exists(): p.unlink()

    generate_release_notes()

    # Platform Launcher Icon Synchronization
    script_dir = Path(__file__).resolve().parent
    import subprocess
    import sys
    print("🔄 Synchronizing Android launcher mipmaps from new source icon...")
    result_android = subprocess.run([sys.executable, str(script_dir / "sync_android_icon_from_source.py")], capture_output=True, text=True)
    if result_android.returncode != 0:
        print(f"❌ Android icon sync failed: {result_android.stderr}")
    else:
        print(result_android.stdout.strip())
    
    ios_sync_script = script_dir / "sync_ios_icon_from_source.py"
    if ios_sync_script.exists():
        print("🔄 Synchronizing iOS xcassets AppIcon from new source icon...")
        result_ios = subprocess.run([sys.executable, str(ios_sync_script)], capture_output=True, text=True)
        if result_ios.returncode != 0:
            print(f"❌ iOS icon sync failed: {result_ios.stderr}")
        else:
            print(result_ios.stdout.strip())
    else:
        print("⏭️ Skipping iOS icon sync (script not yet available)")
