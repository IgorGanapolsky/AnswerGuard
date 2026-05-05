# iOS Instructions

applyTo: "native-ios/**/*.swift"

## Architecture

- UI is SwiftUI.
- Production spam blocking requires a Call Directory extension and `CXCallDirectoryProvider`.
- Keep phone-number datasets sorted and deterministic for extension loading.

## Compliance

- Follow App Review Guideline 2.5.12 for CallKit/call-identification apps.
- Describe blocking criteria clearly in metadata and review notes.
- Do not use call-protection data for unrelated tracking, profiling, sharing, or sale.

## Testing

- Unit tests: `native-ios/AnswerGuardTests/**`
- UI tests: `native-ios/AnswerGuardUITests/**`
- Local simulator gate: `cd native-ios && xcodebuild test -scheme AnswerGuard -destination 'platform=iOS Simulator,name=iPhone 17'`
