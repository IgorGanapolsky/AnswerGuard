# AnswerGuard Infrastructure

## Branch Strategy

- `develop` is the integration branch.
- `main` is protected and should receive release branches only.
- Feature work should land through pull requests.
- Internal distribution runs from explicit workflow dispatches and protected integration refs.

## Core CI/CD

- `ci.yml`: repository quality and platform checks.
- `security.yml`: security and dependency checks.
- `internal-distribution.yml`: TestFlight and Firebase internal builds.
- `native-release.yml`: store release pipeline.
- `pr-state-machine.yml`: PR status labels and CI state.
- `resolve-bot-comments.yml`: bot review-thread cleanup.
- `store-listing-parity.yml`: iOS/Android listing drift guard.

## Required Secrets

- `APPSTORE_PRIVATE_KEY`
- `APPSTORE_KEY_ID`
- `APPSTORE_ISSUER_ID`
- `MATCH_GIT_URL`
- `MATCH_PASSWORD`
- `MATCH_GIT_BASIC_AUTHORIZATION`
- `ANDROID_KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `FIREBASE_ANDROID_APP_ID`

## Required Variables

- `TESTFLIGHT_INTERNAL_TESTERS`
- `FIREBASE_INTERNAL_TESTERS`
- `FIREBASE_INTERNAL_GROUPS`
- `PR_REQUIRED_CHECKS`

## Store Readiness

Android currently has internal build coverage for call screening. iOS still needs the Call Directory extension target fully wired and provisioned before production submission.
