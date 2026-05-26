# Gemini Project Instructions: AnswerGuard

This file contains foundational mandates for Gemini CLI within the AnswerGuard repository.

## Engineering Standards

- **Contextual Precedence:** These instructions take absolute precedence over general workflows.
- **Testing:** Always run appropriate tests before declaring a task complete.
  - Python tests: `python3 -m pytest -q scripts/tests/`
  - Android tests: `cd native-android && ./gradlew testDebugUnitTest`
  - iOS tests: `cd native-ios && xcodebuild test -project AnswerGuard.xcodeproj -scheme AnswerGuard`
  - Maestro smoke tests: `maestro test .maestro/ios-smoke-test.yaml` or `maestro test .maestro/smoke-test.yaml`
  - AI Code Review: Automated via SonarQube & Gitar Agentic Analysis (Quality Gate enforced).

## Core Workflows

- **Branching:** Changes should move through feature branches into `develop`, then promoted to `main`.
- **Hooks:** Git hooks are installed via `make install-hooks`. Ensure `scripts/pre-commit` is respected.

## Technical Context

- **Data Privacy:** Rigorous checks are in place to ensure call screening data and contacts never leave the device.
- **Platform Parity:** Maintain feature parity between Android and iOS versions (Call Screening, Personal Blocklist, Contact Identification).
- **Privacy:** Adhere to the policies in `PRIVACY_POLICY.md`.

## GitHub Actions Efficiency & Budget Capping

- **No Idle Scheduled Cron Triggers**: Non-critical background workflows (e.g., wiki sync, posthog dashboards, rating snapshots) must not have automated cron `schedule:` triggers to prevent background resource/budget waste.
- **Manual and PR-Driven CI Execution**: Automated workflows must utilize `workflow_dispatch` for manual on-demand execution. Keep GitHub Actions active only for PR CI verification gates, security/secrets scans, and explicitly triggered deployments.
