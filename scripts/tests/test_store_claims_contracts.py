from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

CLAIM_FILES = [
    "docs/index.html",
    "docs/delete-data.html",
    "native-android/fastlane/metadata/android/en-US/title.txt",
    "native-android/fastlane/metadata/android/en-US/full_description.txt",
    "native-android/fastlane/metadata/android/en-US/changelogs/default.txt",
    "scripts/stellar_brand_generator_v2.py",
    "scripts/generate_ios_store_creatives.py",
]

BANNED_SHIPPED_CLAIMS = [
    "AI call screening &amp; deepfake protection",
    "AI Call Shield",
    "Stop deepfakes",
    "On-device Gemini intelligence",
    "Voice deepfake detection",
    "Gemini Nano analyzes",
    "Gemini Nano decodes",
    "voice deepfakes",
    "AUTONOMOUS AI CALL SHIELD",
    "AI INTENT ANALYSIS",
    "GEMINI READY",
    "DEEPFAKE DEFENSE",
    "Block AI voice clones",
    "Household Sync",
    "private encrypted channels",
    "SHARPEN YOUR DRAW",
    "RANGE COMMANDS",
    "Randomized signals for dry-fire",
    "boxing, MMA, and HIIT",
    "B2B Compliance",
    "strict scam defense",
]


def test_store_claims_match_shipped_call_screening_features():
    combined = "\n".join((ROOT / path).read_text(encoding="utf-8") for path in CLAIM_FILES)

    for phrase in BANNED_SHIPPED_CLAIMS:
        assert phrase not in combined


def test_ios_screenshot_validator_matches_current_answer_guard_assets():
    source = (ROOT / "scripts/refresh_ios_screenshot_creatives.py").read_text(encoding="utf-8")

    for screenshot in ["01_AI.png", "02_PRIVATE.png", "03_FRAUD.png", "04_HOUSEHOLD.png"]:
        assert screenshot in source

    assert "1_setup.png" not in source
    assert "2_active.png" not in source
