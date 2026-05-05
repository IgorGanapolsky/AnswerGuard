# Android Instructions

applyTo: "native-android/**/*.kt"

## Architecture

- Keep call-screening verdict logic deterministic and unit-testable.
- Avoid Android restricted permissions unless there is a documented store/compliance reason.
- Treat `CallScreeningService` timeout behavior as product-critical.

## Compose

- Build compact operational screens, not marketing pages.
- Make status, enablement, restore, and purchase actions reachable without layout overflow.
- Keep display text consistent with privacy-first spam protection.

## Testing

- Unit tests: `native-android/app/src/test/**`
- Local gate: `cd native-android && ./gradlew testDebugUnitTest assembleDebug`
- Release gate: `cd native-android && ./gradlew bundleRelease assembleRelease`
