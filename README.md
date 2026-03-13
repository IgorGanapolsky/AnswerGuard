# AnswerGuard

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform: iOS](https://img.shields.io/badge/iOS-18%2B-blue?logo=apple)](native-ios/)
[![Platform: Android](https://img.shields.io/badge/Android-8%2B-green?logo=android)](native-android/)
[![CI](https://github.com/IgorGanapolsky/AnswerGuard/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/IgorGanapolsky/AnswerGuard/actions/workflows/ci.yml)
[![Swift 6](https://img.shields.io/badge/Swift-6-F05138?logo=swift&logoColor=white)](native-ios/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)](native-android/)

`AnswerGuard` is a native iOS + Android bootstrap for a privacy-first spam and scam call screening app.

This repository currently preserves the mobile project structure, release automation, Fastlane setup, and GitHub Actions backbone adapted from `Random-Timer`. The copied app code is still scaffold material, not a finished call-blocking product. Build and release infrastructure is in place; the actual spam-call experience still needs to be implemented.

## What’s Included

- Native Android project in [`native-android/`](native-android/)
- Native iOS project in [`native-ios/`](native-ios/)
- GitHub Actions CI/CD in [`.github/workflows/`](.github/workflows/)
- Fastlane setup for Android and iOS store delivery
- Shared local commands in [`Makefile`](Makefile)

## Build & Test

The [`Makefile`](Makefile) is the canonical task entrypoint.

### Quick Verification

```bash
make verify
```

### iOS

```bash
cd native-ios
xcodebuild -scheme AnswerGuard -showdestinations
xcodebuild test -scheme AnswerGuard -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.1'
```

If `iPhone 17` is unavailable, pick any available iPhone simulator from `-showdestinations`.

### Android

```bash
cd native-android
./gradlew testDebugUnitTest assembleDebug lint
```

### Helpful Local Commands

```bash
make run-ios-sim
make run-android-emulator
make verify-ios
make verify-android
make maestro-ios
make maestro-android
```

## Product Direction

Target positioning for `AnswerGuard`:

- Reduce spam and bot call interruptions
- Help users avoid missing legitimate unknown callers
- Stay privacy-first and transparent about decisions

## License

[MIT](LICENSE) — Igor Ganapolsky
