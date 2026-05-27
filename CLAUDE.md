# Ralph Mode: 24/7 Autonomous Verification Loop

This file activates the **Ralph Loop** for Gemini CLI. When Ralph Mode is active, the agent must autonomously pursue all project goals until they are fully verified.

## Primary Objectives

1.  **Fully Verified E2E**: All Maestro and Playwright flows must pass.
2.  **Code Coverage**: Ensure critical logic in Android and iOS has high unit test coverage.
3.  **Publication Readiness**: Validate that v1.2.7 is ready for store submission.
4.  **ThumbGate Integrity**: Maintain an active and blocking pre-action check environment.
5.  **Agentic Quality**: Pass the Sonar & Gitar AI Quality Gate on every PR.

## Execution Loop (The Ralph Loop)

The agent should follow this iterative cycle:
- **Analyze**: Check the current status of all objectives.
- **Execute**: Perform the necessary tasks (coding, testing, fixing).
- **Verify**: Run the proof harnesses and test suites.
- **Repeat**: Continue until a "Done" state is empirically proven.

## Verification Harnesses

- **ThumbGate Proof**: `thumbgate prove automation`
- **Maestro**: `make maestro-ios` / `make maestro-android`
- **Playwright**: `cd tests/playwright && npm run verify`
- **Unit Tests**: `make verify`
- **Coverage threshold**: `cd native-android && ./gradlew :app:jacocoCoverageVerification`

## ⚠️ Before claiming any fix is "done" or "shipped"

Run the **`verify-answerguard-fix`** skill. Don't skip it because a change feels
small — the contract is verification-completeness, not change-size proportionality.

The skill runs all four harnesses and reports actual numbers (test count,
% coverage per metric, pass/fail per harness). It also tells you when a
harness can't run (no emulator, no JDK) so you fail honestly instead of
silently omitting.

This is enforced two ways:
1. **Local**: `scripts/pre-commit` runs `:app:testDebugUnitTest` and
   `:app:jacocoCoverageVerification` automatically when any
   `native-android/app/src/{main,test}/**` file is staged. To bypass for
   a docs-only emergency: `ANSWERGUARD_SKIP_ANDROID_VERIFY=1 git commit ...`.
2. **CI**: `.github/workflows/ci.yml`'s `android` job runs the same two
   commands. Branch protection requires this job to pass before merge.

The skill exists so the in-session agent does the proof BEFORE reporting,
not after the user has to ask "are you sure?"
