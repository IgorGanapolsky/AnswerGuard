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

# 2026 FLAT, high-contrast brand icon palette.
# Two colors total: a gentle (low-contrast, 2-stop) teal->emerald VERTICAL fill
# plus ONE flat white shield with a CHECKMARK knocked out (the teal gradient
# shows through the check = "answered / verified / safe"). No gloss, no radial
# glow, no drop shadows, no bevel/3D, no decorative swoosh. Clean like
# Spotify / Visual Voicemail.
ICON_GRAD_TOP = (21, 195, 154)      # #15C39A  bright teal-green (top)
ICON_GRAD_BOTTOM = (14, 158, 134)   # #0E9E86  deep emerald (bottom)
ICON_WHITE = (255, 255, 255)        # #FFFFFF  flat shield glyph

# Content radius (as a fraction of the half-canvas) for the Android adaptive
# foreground glyph. Must stay <= 0.60 so it sits comfortably inside the 0.66
# adaptive safe circle with a clear transparent margin ring.
ADAPTIVE_CONTENT_RADIUS_FRAC = 0.58

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


def _vertical_gradient(size, top, bottom):
    """Build a fully-opaque RGB vertical (top->bottom) 2-stop gradient image.

    Low-contrast / gentle on purpose: a flat-looking fill, NOT a gloss. The two
    stops are close in luminance so the result reads as a single solid teal.
    """
    img = Image.new("RGB", (size, size))
    px = img.load()
    denom = max(size - 1, 1)
    for y in range(size):
        t = y / denom
        row = _lerp(top, bottom, t)
        for x in range(size):
            px[x, y] = row
    return img


def _shield_mask(big, cx, cy, half_w, top_y, bottom_y):
    """Return an L-mode mask of a clean, modern flat shield silhouette."""
    import math
    top = top_y
    shoulder_y = top_y + (bottom_y - top_y) * 0.16
    mid_y = top_y + (bottom_y - top_y) * 0.52
    pts = [(cx, top), (cx + half_w, shoulder_y), (cx + half_w, mid_y)]
    steps = 28
    for i in range(1, steps + 1):
        t = i / steps
        ang = t * (math.pi / 2)
        x = cx + half_w * math.cos(ang) * (1 - 0.10 * t)
        y = mid_y + (bottom_y - mid_y) * math.sin(ang)
        pts.append((x, y))
    right = pts[3:]
    for x, y in reversed(right):
        pts.append((cx - (x - cx), y))
    pts.append((cx - half_w, mid_y))
    pts.append((cx - half_w, shoulder_y))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).polygon(pts, fill=255)
    return mask


def _check_mask(big, cx, cy, half_w, top_y, bottom_y):
    """Return an L-mode mask of a bold, unambiguous CHECKMARK (round caps/joins).

    The classic two-segment tick: START -> low VERTEX -> high END, drawn as a
    round-capped, round-joined stroked polyline (NOT a filled freeform blob).
    Geometry is expressed as fractions of the shield bounding box so it scales
    with any icon size and sits fully inside the white shield with margin.

    Reference (1024 canvas, shield centred ~(512,500)):
        START (405,520) -> VERTEX (480,605) -> END (640,425), stroke ~70px.
    """
    ch = bottom_y - top_y          # shield box height
    w = 2.0 * half_w               # shield box width

    # Vertices as fractions of the shield bounding box (x from left edge,
    # y from top edge). Derived from the 1024 reference vertices above.
    left = cx - half_w
    pts = [
        (left + 0.337 * w, top_y + 0.514 * ch),  # START  ~(405,520)
        (left + 0.460 * w, top_y + 0.648 * ch),  # VERTEX ~(480,605)
        (left + 0.722 * w, top_y + 0.364 * ch),  # END    ~(640,425)
    ]
    stroke_w = ch * 0.110          # ~70px on a 635px-tall shield box
    r = stroke_w / 2.0

    mask = Image.new("L", (big, big), 0)
    d = ImageDraw.Draw(mask)
    # Stroke the polyline with ROUND joins+caps: thick line per segment plus a
    # disk at every vertex/endpoint (this is exactly round line caps + joins,
    # with no thin rasterization slivers).
    d.line(pts, fill=255, width=int(round(stroke_w)), joint="curve")
    for (px, py) in pts:
        d.ellipse((px - r, py - r, px + r, py + r), fill=255)
    return mask


def shield_glyph_masks(size, center, half_width, top_y, bottom_y, ss=6):
    """Return (shield_mask, check_mask) at full `size` resolution.

    Masks are supersampled then downscaled for crisp, anti-aliased flat edges.
    """
    big = size * ss
    cx, cy = center[0] * ss, center[1] * ss
    shield = _shield_mask(big, cx, cy, half_width * ss, top_y * ss, bottom_y * ss)
    check = _check_mask(big, cx, cy, half_width * ss, top_y * ss, bottom_y * ss)
    shield = shield.resize((size, size), Image.Resampling.LANCZOS)
    check = check.resize((size, size), Image.Resampling.LANCZOS)
    return shield, check


def _glyph_alpha(shield, check):
    """Flat white glyph alpha = shield with the CHECKMARK knocked out.

    Returns an L-mode alpha mask (white where the glyph is opaque); the teal
    gradient shows through the knocked-out check.
    """
    from PIL import ImageChops
    # Knock the check out of the shield (only inside the shield).
    check_in = ImageChops.multiply(check, shield)
    glyph = ImageChops.subtract(shield, check_in)
    return glyph


def render_flat_icon(size, opaque=True):
    """Full-bleed flat icon: teal gradient bg + white shield (check knocked out).

    Returns an RGB image when opaque=True (iOS/legacy), else RGBA.
    """
    grad = _vertical_gradient(size, ICON_GRAD_TOP, ICON_GRAD_BOTTOM)
    shield_h = int(size * 0.62)
    top_y = (size - shield_h) // 2
    bottom_y = top_y + shield_h
    half_w = int(size * 0.30)
    cx, cy = size // 2, (top_y + bottom_y) // 2
    shield, check = shield_glyph_masks(size, (cx, cy), half_w, top_y, bottom_y)
    glyph = _glyph_alpha(shield, check)
    white = Image.new("RGB", (size, size), ICON_WHITE)
    out = grad.copy()
    out.paste(white, (0, 0), glyph)
    if opaque:
        return out.convert("RGB")
    rgba = out.convert("RGBA")
    return rgba


def render_adaptive_foreground(size):
    """Android adaptive FOREGROUND: white glyph on TRANSPARENT bg.

    The glyph is sized so its content radius stays <= ADAPTIVE_CONTENT_RADIUS_FRAC
    of the half-canvas, leaving a clear transparent margin inside the safe zone.
    """
    # Fit the shield's bounding box inside the target content circle.
    half = size / 2.0
    content_r = ADAPTIVE_CONTENT_RADIUS_FRAC * half
    # Shield bbox: height = shield_h, width ~= 2*half_w. Use the larger to fit.
    # Pick a shield whose half-diagonal of its bbox == content_r.
    # Use ratios consistent with render_flat_icon (h=0.62*s, w=0.60*s of the
    # mark box). We solve for a mark box `m` centered on the canvas.
    h_ratio, w_ratio = 0.62, 0.60
    # half-diagonal of the mark bbox in units of m:
    import math
    half_diag_per_m = 0.5 * math.hypot(h_ratio, w_ratio)
    m = content_r / half_diag_per_m
    shield_h = int(m * h_ratio)
    half_w = int(m * w_ratio / 2.0)
    cx = cy = size // 2
    top_y = cy - shield_h // 2
    bottom_y = top_y + shield_h
    shield, check = shield_glyph_masks(size, (cx, cy), half_w, top_y, bottom_y)
    glyph = _glyph_alpha(shield, check)
    fg = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white = Image.new("RGBA", (size, size), (255, 255, 255, 255))
    fg.paste(white, (0, 0), glyph)
    return fg


def generate_stellar_icon():
    size = 1024
    # FLAT full-bleed mark: gentle teal gradient bg + ONE white shield with a
    # CHECKMARK knocked out. Fully opaque (OS applies the rounded mask).
    img = render_flat_icon(size, opaque=True)

    icon_path = ANDROID_IMAGES / "icon.png"
    icon_path.parent.mkdir(parents=True, exist_ok=True)
    # Save a high-res 1024 source so iOS 1024 is downscaled (crisp), not upscaled.
    source_1024 = ANDROID_IMAGES / "icon-1024.png"
    img.save(source_1024)
    img.resize((512, 512), Image.Resampling.LANCZOS).save(icon_path)
    print(f"🌟 Generated Flat Icon (opaque 1024): {source_1024}")
    print(f"🌟 Generated Flat Icon (512 source): {icon_path}")

    # Android adaptive FOREGROUND: white glyph on transparent bg, inset inside
    # the adaptive safe zone (content radius <= 0.60 of half-canvas).
    fg = render_adaptive_foreground(size)
    fg_path = REPO_ROOT / "native-android" / "branding" / "icon-foreground.png"
    fg_path.parent.mkdir(parents=True, exist_ok=True)
    fg.save(fg_path)
    print(f"🌟 Generated Adaptive Foreground (transparent 1024): {fg_path}")

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
    # Flat brand mark on the right (reuse the icon mark, no gloss).
    mark_size = 360
    mark = render_flat_icon(mark_size, opaque=True)
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
- New launcher icon: clean shield with a verified checkmark.
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
