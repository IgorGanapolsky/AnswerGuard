from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ANDROID_ROOT = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard"
IOS_ROOT = ROOT / "native-ios/AnswerGuard/Sources"


def _repo_sources(root: Path, suffix: str) -> str:
    return "\n".join(path.read_text(encoding="utf-8") for path in root.rglob(f"*{suffix}"))


def test_random_timer_runtime_is_not_part_of_android_answer_guard():
    source = _repo_sources(ANDROID_ROOT, ".kt")

    for legacy_snippet in [
        "TimerConfig",
        "TimerRepository",
        "TimerForegroundService",
        "AIVoiceCalloutManager",
        "timer_started",
        "timer_completed",
        "Voice Callouts",
    ]:
        assert legacy_snippet not in source


def test_answer_guard_android_runtime_uses_call_screening_language():
    source = _repo_sources(ANDROID_ROOT, ".kt")

    for required_snippet in [
        "Call Screening",
        "SpamVerdict",
        "AnswerGuardScreeningService",
        "ROLE_CALL_SCREENING",
        "call_screening_enabled",
        "first_protection_enabled",
    ]:
        assert required_snippet in source


def test_answer_guard_ios_primary_surface_uses_call_protection_language():
    source = _repo_sources(IOS_ROOT, ".swift")

    for required_snippet in [
        "AnswerGuard",
        "Spam & Scam Call Protection",
        "Call Screening",
        "CallDirectoryManager",
        "Call Blocking & Identification",
    ]:
        assert required_snippet in source
