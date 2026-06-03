#!/usr/bin/env python3
"""Regenerate Android mipmap launcher PNGs from the single square source icon."""

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover
    raise SystemExit(f"Pillow is required: {exc}")


def run(source: Path, res_dir: Path, foreground_source: Path | None = None) -> bool:
    if not source.exists():
        print(f"❌ Error: source icon missing at {source}", file=sys.stderr)
        return False
    if not res_dir.exists():
        print(f"❌ Error: res directory missing at {res_dir}", file=sys.stderr)
        return False

    src = Image.open(source).convert("RGBA")
    if src.width != src.height:
        print(f"❌ Error: source icon is not square ({src.width}x{src.height})", file=sys.stderr)
        return False
    if src.width < 512:
        print(f"⚠️ Warning: source icon is small ({src.width}x{src.height}), quality may be degraded", file=sys.stderr)

    fg_src = src
    if foreground_source:
        if foreground_source.exists():
            fg_src = Image.open(foreground_source).convert("RGBA")
            if fg_src.width != fg_src.height:
                print(
                    f"❌ Error: foreground source is not square ({fg_src.width}x{fg_src.height})",
                    file=sys.stderr,
                )
                return False
        else:
            print(
                f"⚠️ Warning: foreground source not found at {foreground_source}, using base icon",
                file=sys.stderr,
            )

    # Standard densities and their target resolutions (in pixels)
    # Legacy: Standard launcher icon size
    # Foreground: Foreground layer of adaptive launcher icon (supports dynamic scaling)
    mipmaps = {
        "mipmap-mdpi": {"legacy": 48, "foreground": 108},
        "mipmap-hdpi": {"legacy": 72, "foreground": 162},
        "mipmap-xhdpi": {"legacy": 96, "foreground": 216},
        "mipmap-xxhdpi": {"legacy": 144, "foreground": 324},
        "mipmap-xxxhdpi": {"legacy": 192, "foreground": 432},
    }

    written_count = 0
    for folder, sizes in mipmaps.items():
        target_folder = res_dir / folder
        target_folder.mkdir(parents=True, exist_ok=True)

        # 1. Legacy and round legacy icons
        legacy_size = sizes["legacy"]
        legacy_img = src.resize((legacy_size, legacy_size), Image.Resampling.LANCZOS)
        for name in ["ic_launcher_legacy.png", "ic_launcher_round_legacy.png"]:
            out_path = target_folder / name
            legacy_img.save(out_path, format="PNG")
            written_count += 1

        # 2. Foreground and monochrome adaptive layers
        fg_size = sizes["foreground"]
        
        fg_img = fg_src.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
        for name in ["ic_launcher_foreground.png", "ic_launcher_monochrome.png"]:
            out_path = target_folder / name
            fg_img.save(out_path, format="PNG")
            written_count += 1

    print(f"✓ Synchronized {written_count} Android launcher icon assets successfully.")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Regenerate Android mipmap icons from source PNG")
    parser.add_argument(
        "--source",
        default="native-android/fastlane/metadata/android/en-US/images/icon.png",
        help="Source icon PNG (512x512 or 1024x1024)",
    )
    parser.add_argument(
        "--res",
        default="native-android/app/src/main/res",
        help="Path to main Android res/ folder",
    )
    parser.add_argument(
        "--foreground-source",
        default="native-android/branding/icon-foreground.png",
        help="Optional adaptive foreground source PNG",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    source_path = repo_root / args.source
    res_path = repo_root / args.res

    foreground_path = repo_root / args.foreground_source if args.foreground_source else None
    success = run(source_path, res_path, foreground_path)
    return 0 if success else 1


if __name__ == "__main__":
    raise SystemExit(main())
