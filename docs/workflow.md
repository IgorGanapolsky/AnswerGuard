# AnswerGuard Workflow

AnswerGuard changes move through feature branches into `develop`, then through a protected promotion into `main`.

Canonical proof commands:

```bash
python3 -m pytest -q scripts/tests/
cd native-android
./gradlew testDebugUnitTest
cd ../native-ios
xcodebuild test -project AnswerGuard.xcodeproj -scheme AnswerGuard
cd ..
maestro test .maestro/ios-smoke-test.yaml
```

Python automation tests live in `scripts/tests`. Android, iOS, store metadata, signing, and release automation are enforced by GitHub Actions before protected branches can move.
