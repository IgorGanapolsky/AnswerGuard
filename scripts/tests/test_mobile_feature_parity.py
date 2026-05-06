from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

ANDROID_MANIFEST = ROOT / "native-android/app/src/main/AndroidManifest.xml"
ANDROID_MAIN = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/MainActivity.kt"
ANDROID_SCREENING_SERVICE = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/screening/AnswerGuardScreeningService.kt"
ANDROID_ROLE_ONBOARDING = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/screening/RoleOnboardingActivity.kt"
ANDROID_VERDICT_ENGINE = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/screening/SpamVerdictEngine.kt"
ANDROID_PRO_MANAGER = ROOT / "native-android/app/src/main/java/com/igorganapolsky/answerguard/billing/ProManager.kt"
IOS_PRO_MANAGER = ROOT / "native-ios/AnswerGuard/Sources/Services/ProManager.swift"
IOS_HOME = ROOT / "native-ios/AnswerGuard/Sources/UI/Screens/AnswerGuardHomeScreen.swift"
IOS_CALL_DIRECTORY_MANAGER = ROOT / "native-ios/AnswerGuard/Sources/Services/CallDirectoryManager.swift"
IOS_CALL_DIRECTORY_EXTENSION = ROOT / "native-ios/CallDirectoryExtension/CallDirectoryHandler.swift"


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def test_android_declares_real_call_screening_capability():
    manifest = _read(ANDROID_MANIFEST)
    role_onboarding = _read(ANDROID_ROLE_ONBOARDING)

    assert "android.telecom.CallScreeningService" in manifest
    assert "android.permission.BIND_SCREENING_SERVICE" in manifest
    assert ".screening.AnswerGuardScreeningService" in manifest
    assert "RoleManager.ROLE_CALL_SCREENING" in role_onboarding


def test_android_removed_timer_runtime_surface():
    manifest = _read(ANDROID_MANIFEST)
    main = _read(ANDROID_MAIN)

    banned_snippets = [
        "USE_EXACT_ALARM",
        "SCHEDULE_EXACT_ALARM",
        "FOREGROUND_SERVICE_SPECIAL_USE",
        "TimerForegroundService",
        "TimerSetupScreen",
        "ActiveTimerScreen",
    ]
    for snippet in banned_snippets:
        assert snippet not in manifest
        assert snippet not in main


def test_android_verdict_engine_has_conservative_privacy_first_outcomes():
    source = _read(ANDROID_VERDICT_ENGINE)
    service = _read(ANDROID_SCREENING_SERVICE)

    for verdict in ["ALLOW", "SILENCE", "BLOCK"]:
        assert verdict in source
        assert verdict in service
    assert "return SpamVerdict.ALLOW" in source
    assert "UserBlocklist.contains" in source
    assert "callDetails.handle" in service


def test_ios_declares_call_directory_product_surface():
    home = _read(IOS_HOME)
    manager = _read(IOS_CALL_DIRECTORY_MANAGER)
    extension = _read(IOS_CALL_DIRECTORY_EXTENSION)

    for snippet in [
        "Spam & Scam Call Protection",
        "Call Screening",
        "Call Blocking & Identification",
        "Your call data never leaves your phone",
    ]:
        assert snippet in home
    assert "CXCallDirectoryManager" in manager
    assert "CXCallDirectoryProvider" in extension


def test_pro_surface_stays_answer_guard_specific():
    android_main = _read(ANDROID_MAIN)
    ios_pro_manager = _read(IOS_PRO_MANAGER)

    assert "AnswerGuard Pro" in android_main
    assert "advanced spam rules" in android_main
    assert "func unlockEliteForDebug()" not in ios_pro_manager


def test_android_pro_buttons_report_billing_progress_and_failures():
    android_main = _read(ANDROID_MAIN)
    android_pro_manager = _read(ANDROID_PRO_MANAGER)

    for snippet in [
        "proActionInProgress",
        "proStatusMessage",
        "Connecting to Google Play...",
        "Could not open Google Play billing.",
        "No active Pro purchase found",
        "enabled = !actionInProgress",
    ]:
        assert snippet in android_main

    assert "val launched =" in android_main
    assert "val restored =" in android_main
    assert "launchProPurchase(" in android_main
    assert "productID = ProManager.BASE_PRODUCT_ID" not in android_main
    assert "const val PRO_PRODUCT_ID = ELITE_PRODUCT_ID" in android_pro_manager
    assert "launchProPurchase(" in android_pro_manager
    assert "ensureBillingReady()" in android_pro_manager
    assert "CompletableDeferred<BillingResult>" in android_pro_manager
