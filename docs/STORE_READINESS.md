# AnswerGuard Store Readiness

Status as of 2026-05-05: Android now builds as an AnswerGuard call-screening app for internal distribution. iOS TestFlight distribution infrastructure is working. The iOS Call Directory extension target (`com.igorganapolsky.answerguard.calldirectory`) is now wired into the Xcode project and shares state with the main app via App Group `group.com.igorganapolsky.answerguard`; production App Store submission still requires the matching Developer Portal bundle ID + provisioning profile and the store assets refresh.

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
- Delivered iOS TestFlight internal build `1.2.6` build `133` to `iganapolsky@gmail.com`.
- Delivered Android Firebase internal build `1.2.6 (4)` to the internal tester path.
- Replaced Android timer UI/runtime code with Android call-screening onboarding, local spam verdicts, and reduced permissions.

Current local caveat: GitHub Actions secrets are write-only, so local verification can confirm the pipeline result but cannot print the stored signing and Firebase secret values.

## Platform Requirements

### iOS

Apple requires call blocking/identification apps to use a Call Directory app extension. The extension supplies phone numbers to identify or block through a `CXCallDirectoryProvider`.

Apple App Review guideline 2.5.12 also requires CallKit/SMS fraud apps to block only confirmed spam, clearly describe the blocking/identification criteria in marketing text, and not use data from those tools for unrelated profiling, tracking, sharing, or sale.

Required production work:

- ~~Add an iOS Call Directory extension target.~~ Done — target `CallDirectoryExtension` (`com.igorganapolsky.answerguard.calldirectory`) wired into `native-ios/AnswerGuard.xcodeproj` with App Group entitlement, sources, embed-extension build phase, and target dependency from the main app.
- ~~Implement `CXCallDirectoryProvider` with a verified spam/allow/label data source.~~ Done — `native-ios/CallDirectoryExtension/CallDirectoryHandler.swift` handles full and incremental requests via the shared `SpamDatabase`.
- ~~Add in-app controls for enabling, refreshing, and explaining the Call Directory list.~~ Done — `AnswerGuardHomeScreen` plus `CallDirectoryManager.block(_:)` / `unblock(_:)` / `reloadExtension()` / `refreshStatus()`.
- ~~Add tests for sorted phone-number entries, duplicate handling, extension reload failure, and empty/offline datasets.~~ Done at the data-source level in `native-ios/AnswerGuardTests/SpamDatabaseTests.swift` (sort, dedup, add/remove, incremental deltas, empty list).
- Update App Store metadata, screenshots, privacy policy, and review notes to describe real call-identification behavior. (Still pending — copy/screenshots task.)

App Group / bundle identifiers in use:

- App Group: `group.com.igorganapolsky.answerguard`
- Main app: `com.igorganapolsky.answerguard`
- Widget extension: `com.igorganapolsky.answerguard.widget`
- Call Directory extension: `com.igorganapolsky.answerguard.calldirectory`

Manual Xcode / Apple Developer Portal steps still required:

- Register the bundle ID `com.igorganapolsky.answerguard.calldirectory` in the Apple Developer Portal with the App Groups capability and assign it to the App Group `group.com.igorganapolsky.answerguard`.
- Create matching App Store provisioning profiles via fastlane match so the `match AppStore com.igorganapolsky.answerguard` profile includes App Groups and the `match AppStore com.igorganapolsky.answerguard.calldirectory` profile referenced in the target build settings resolves.
- Ensure the App Group is enabled on the main app's primary App ID `com.igorganapolsky.answerguard`; the 2026-05-19 internal distribution run failed because the main App Store profile did not include `com.apple.security.application-groups` / `group.com.igorganapolsky.answerguard`.
- `native-ios/AnswerGuardTests/SpamDatabaseTests.swift` and `ContactsServiceTests.swift` are now members of the `AnswerGuardTests` target in the `.xcodeproj`. Local execution of `make verify-ios` still requires a full Xcode install/selection.

Official references:

- https://developer.apple.com/documentation/callkit/identifying-and-blocking-calls
- https://developer.apple.com/app-store/review/guidelines/
- https://developer.apple.com/app-store/app-privacy-details/
- https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api

### Android

Android call screening uses `android.telecom.CallScreeningService`, registered in the manifest with `android.permission.BIND_SCREENING_SERVICE`. The user chooses one app for `RoleManager.ROLE_CALL_SCREENING`. Incoming-call screening must respond within 5 seconds.

Google Play treats SMS and Call Log permissions as highly sensitive. If the app requests restricted call-log permissions, it must qualify as a default Phone/Assistant handler or receive an approved exception. A call-screening app should avoid restricted call-log permissions unless absolutely required and justified.

Completed for internal Android builds:

- `CallScreeningService` implementation.
- Manifest service registration with `BIND_SCREENING_SERVICE`.
- RoleManager request flow for `ROLE_CALL_SCREENING`.
- Local-first spam verdict engine that responds synchronously.
- `ContactsAllowlist` short-circuits the spam engine when the caller matches the user's contacts (requires `READ_CONTACTS`, declared in `AndroidManifest.xml`).
- In-app `BlocklistScreen` for managing the user-maintained block list, plus `ContactsCard` for requesting contacts permission.
- `RoleOnboardingTest` instrumentation test (Espresso/UIAutomator) for the role request dialog — staged but not yet running in CI (no instrumentation job exists).

Required production work:

- Add Play Console privacy-policy disclosure for `READ_CONTACTS` (on-device matching only, never uploaded).
- Add device/emulator tests for role onboarding, unknown caller handling, contacts behavior, block/allow decisions, and no-permission fallback. Some Maestro flows now exist in `.maestro/` covering content render, deep link, pro upgrade attempts, and pro restore; expand as testTags are added across the Compose tree.
- Complete Play Console App Content: Data safety, privacy policy, sensitive permission declarations (incl. `READ_CONTACTS`), target API, content rating, ads, and closed testing.

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

- iOS Call Directory extension target is now wired into the Xcode project (`CallDirectoryExtension` target, bundle ID `com.igorganapolsky.answerguard.calldirectory`, embed-extension build phase, App Group entitlement). Still pending: Apple Developer Portal bundle-ID registration with App Groups capability, main-app App Group profile refresh, and a matching match-provisioned App Store profile.
- Store screenshots still need to show the caller-screening experience.
- Play Console App Content and closed-testing evidence still need credentialed verification before production rollout.
