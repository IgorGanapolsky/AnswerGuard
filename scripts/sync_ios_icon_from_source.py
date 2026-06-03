#!/usr/bin/env python3
"""Regenerate iOS AppIcon.appiconset PNGs from a single square source icon."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit(f"Pillow is required: {exc}")

IOS_ICON_BACKGROUND = (6, 33, 30, 255)


def _parse_pixels(entry: Dict[str, Any]) -> int:
    size_text = str(entry.get("size", "0x0")).strip().lower()
    scale_text = str(entry.get("scale", "1x")).strip().lower()
    base = float(size_text.split("x")[0])
    scale = float(scale_text.replace("x", ""))
    return int(round(base * scale))


def _flatten_for_ios_icon(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    background = Image.new("RGBA", rgba.size, IOS_ICON_BACKGROUND)
    background.alpha_composite(rgba)
    return background.convert("RGB")


def run(source: Path, appiconset: Path) -> Dict[str, Any]:
    contents_path = appiconset / "Contents.json"
    if not source.exists():
        return {"status": "error", "reason": f"source icon missing: {source}"}
    if not contents_path.exists():
        return {"status": "error", "reason": f"Contents.json missing: {contents_path}"}

    try:
        payload = json.loads(contents_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        return {"status": "error", "reason": f"invalid Contents.json: {exc}"}

    images = payload.get("images", [])
    if not isinstance(images, list):
        return {"status": "error", "reason": "Contents.json has invalid images list"}

    src = Image.open(source).convert("RGBA")
    if src.width != src.height:
        return {"status": "error", "reason": f"source icon is not square ({src.width}x{src.height})"}
    written = []
    for image in images:
        if not isinstance(image, dict):
            continue
        filename = image.get("filename")
        if not filename:
            continue
        pixels = _parse_pixels(image)
        if pixels <= 0:
            continue
        out = appiconset / str(filename)
        resized = src.resize((pixels, pixels), Image.Resampling.LANCZOS)
        resized = _flatten_for_ios_icon(resized)
        resized.save(out, format="PNG")
        written.append({"file": str(out), "pixels": pixels})

    return {"status": "ok", "written_count": len(written), "files": written}


def main() -> int:
    parser = argparse.ArgumentParser(description="Regenerate iOS iconset from source PNG")
    parser.add_argument(
        "--source",
        default="native-android/fastlane/metadata/android/en-US/images/icon.png",
        help="Source icon PNG",
    )
    parser.add_argument(
        "--appiconset",
        default="native-ios/AnswerGuard/Resources/Assets.xcassets/AppIcon.appiconset",
        help="Path to AppIcon.appiconset",
    )
    args = parser.parse_args()

    result = run(Path(args.source), Path(args.appiconset))
    print(json.dumps(result, indent=2))
    return 0 if result.get("status") == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())
