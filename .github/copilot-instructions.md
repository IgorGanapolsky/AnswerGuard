# GitHub Copilot Instructions for AnswerGuard

This repo is a native mobile app:

- Android: Kotlin + Jetpack Compose + Hilt in `native-android/`
- iOS: SwiftUI + Swift Concurrency in `native-ios/`

The product is AnswerGuard: privacy-first spam and scam call protection. Do not reintroduce Random Timer product code, timer claims, alarm permissions, workout language, or tactical/audio workflows.

## Non-Negotiables

- Keep call protection conservative: block confirmed spam, silence suspicious callers, allow normal unknown callers by default.
- Avoid restricted phone permissions unless the feature truly requires them and the store declaration is updated.
- Never upload call history, contacts, or personal block lists for advertising or resale.
- Never commit secrets, API keys, keystores, provisioning profiles, or private credentials.
- Run the relevant local test/build gate before claiming work is done.

## Android Guidance

- `AnswerGuardScreeningService` owns incoming-call decisions.
- `SpamVerdictEngine` must remain synchronous and fast enough for Android's call-screening timeout.
- Use `RoleManager.ROLE_CALL_SCREENING` for onboarding.
- Keep Compose UI thin; push durable business rules into testable Kotlin.
- Default verification:
  - `cd native-android && ./gradlew testDebugUnitTest assembleDebug`
  - For release changes: `cd native-android && ./gradlew bundleRelease assembleRelease`

## iOS Guidance

- Production call blocking must use a Call Directory extension target.
- Keep App Store metadata aligned with Apple Guideline 2.5.12: explain criteria, block only confirmed spam, and do not repurpose call-protection data.
- Prefer SwiftUI, `async/await`, and explicit error handling over force unwraps.

## Release Discipline

- `develop` is the integration branch.
- `main` should receive release branches only.
- Use the GitHub Actions internal distribution workflow for TestFlight/Firebase validation before store submission.
