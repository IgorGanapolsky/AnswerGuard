# AnswerGuard Store Readiness

Status as of 2026-05-05: the inherited native projects build and test locally, but AnswerGuard is not production store-ready as a call-screening app. The current app still implements the Random Timer timer experience with AnswerGuard branding.

## Local Gates

- Android unit tests and debug build: `make verify-android`
- Android lint: `cd native-android && ./gradlew lint --no-daemon`
- iOS unit tests: `make verify-ios`
- iOS UI tests: `make verify-ios-ui`

## Internal Build Delivery

The GitHub Actions workflow `.github/workflows/internal-distribution.yml` can deliver:

- iOS TestFlight: `target=ios`
- Android Firebase App Distribution: `target=android_firebase`
- TestFlight plus Firebase App Distribution: `target=ios_firebase`
- TestFlight, Firebase App Distribution, and Google Play internal: `target=all`
- TestFlight plus Google Play internal, skipping Firebase: `target=all_safe`

The workflow mirrors the Random Timer internal-distribution shape: push-triggered
distribution on `develop`/`main`, manual `workflow_dispatch`, ref-to-SHA
resolution, iOS version-lineage guardrails, TestFlight read-back, Firebase
read-back, and signoff statuses for distributed SHAs.

Local shortcuts:

- `make distribute-internal`
- `make distribute-ios`
- `make distribute-firebase`

Required GitHub secrets for TestFlight:

- `MATCH_GIT_URL`
- `MATCH_PASSWORD`
- `MATCH_GIT_BASIC_AUTHORIZATION`
- `APPSTORE_PRIVATE_KEY`
- `APPSTORE_KEY_ID`
- `APPSTORE_ISSUER_ID`
- `ADMIN_TOKEN`

Required GitHub secrets for Android Firebase App Distribution:

- `GOOGLE_SERVICES_JSON`
- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_ANDROID_APP_ID`

Optional GitHub variables for Firebase delivery:

- `FIREBASE_INTERNAL_GROUPS`
- `FIREBASE_INTERNAL_TESTERS`
- `TESTFLIGHT_INTERNAL_TESTERS`

Configured from local `.env` / GitHub variable read-back on 2026-05-05:

- `APPSTORE_PRIVATE_KEY`
- `APPSTORE_KEY_ID`
- `APPSTORE_ISSUER_ID`
- `APPSTORE_VENDOR_NUMBER`
- `POSTHOG_API_KEY`
- `ADMIN_TOKEN`
- `FIREBASE_REQUIRED_TESTER_EMAIL`
- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `FIREBASE_INTERNAL_GROUPS`
- `FIREBASE_INTERNAL_TESTERS`
- `TESTFLIGHT_INTERNAL_TESTERS`

Also completed on 2026-05-05:

- Created Apple Developer Portal bundle IDs for `com.igorganapolsky.answerguard` and `com.igorganapolsky.answerguard.widget`.
- Generated an AnswerGuard Android release keystore locally at `~/.config/answerguard/release.keystore`.
- Built and verified a signed Android release APK locally.

Current blockers observed locally: GitHub Actions secrets are not readable after
they are stored, so Random Timer secrets that exist only in GitHub could not be
copied into AnswerGuard. The remaining missing secrets are:

- `MATCH_GIT_URL`
- `MATCH_PASSWORD`
- `MATCH_GIT_BASIC_AUTHORIZATION`
- `GOOGLE_SERVICES_JSON`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_ANDROID_APP_ID`

Additional Apple blocker: the App Store Connect API key can create Developer
Portal bundle IDs, but `/apps` creation is not allowed through that API key. The
AnswerGuard app record still needs to be created in App Store Connect before
TestFlight upload can succeed.

## Platform Requirements

### iOS

Apple requires call blocking/identification apps to use a Call Directory app extension. The extension supplies phone numbers to identify or block through a `CXCallDirectoryProvider`.

Apple App Review guideline 2.5.12 also requires CallKit/SMS fraud apps to block only confirmed spam, clearly describe the blocking/identification criteria in marketing text, and not use data from those tools for unrelated profiling, tracking, sharing, or sale.

Required work:

- Add an iOS Call Directory extension target.
- Implement `CXCallDirectoryProvider` with a verified spam/allow/label data source.
- Add in-app controls for enabling, refreshing, and explaining the Call Directory list.
- Add tests for sorted phone-number entries, duplicate handling, extension reload failure, and empty/offline datasets.
- Update App Store metadata, screenshots, privacy policy, and review notes to describe real call-identification behavior.

Official references:

- https://developer.apple.com/documentation/callkit/identifying-and-blocking-calls
- https://developer.apple.com/app-store/review/guidelines/
- https://developer.apple.com/app-store/app-privacy-details/
- https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api

### Android

Android call screening uses `android.telecom.CallScreeningService`, registered in the manifest with `android.permission.BIND_SCREENING_SERVICE`. The user chooses one app for `RoleManager.ROLE_CALL_SCREENING`. Incoming-call screening must respond within 5 seconds.

Google Play treats SMS and Call Log permissions as highly sensitive. If the app requests restricted call-log permissions, it must qualify as a default Phone/Assistant handler or receive an approved exception. A call-screening app should avoid restricted call-log permissions unless absolutely required and justified.

Required work:

- Add a `CallScreeningService` implementation.
- Add the manifest service registration with `BIND_SCREENING_SERVICE`.
- Add a RoleManager request flow for `ROLE_CALL_SCREENING`.
- Implement a local-first spam verdict engine that always responds inside 5 seconds.
- Add device/emulator tests for role onboarding, unknown caller handling, contacts behavior, block/allow decisions, and no-permission fallback.
- Complete Play Console App Content: Data safety, privacy policy, sensitive permission declarations if applicable, target API, content rating, ads, and closed testing.

Official references:

- https://developer.android.com/reference/android/telecom/CallScreeningService
- https://developer.android.com/develop/connectivity/telecom
- https://support.google.com/googleplay/android-developer/answer/16558241
- https://support.google.com/googleplay/android-developer/answer/10208820
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/14151465

## Random Timer Reference

Use `../Random-Timer` as the operational template for release automation, metadata sync, screenshot capture, CI gates, App Store Connect scripts, and Play Console scripts. Do not copy timer-specific product claims, permissions, screenshots, or app review notes into AnswerGuard.

Already ported:

- `native-android/gradle.properties`
- Local Android SDK pointer in ignored `native-android/local.properties`
- AnswerGuard display labels for Android and iOS
- Deterministic iOS UI test reset behavior

Remaining store blockers:

- No iOS Call Directory extension exists.
- No Android `CallScreeningService` exists.
- Store metadata still describes an app "in development" rather than a finished product.
- Current screenshots/UI are timer screens, not caller screening.
- Privacy policy still discusses timer settings and analytics, not phone-number/spam-list handling.
- Release signing, App Store Connect, Play Console access, store-console form completion, and closed-testing evidence still need credentialed verification.
