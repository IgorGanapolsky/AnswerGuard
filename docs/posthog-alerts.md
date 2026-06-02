# PostHog Alerts — AnswerGuard launch playbook

PostHog rolled out Logs Alerts in May 2026 (see the 2026-05-29 product email):
checks run every 5 minutes against any insight or query, and notify on threshold
breach. The four alerts below are the highest-ROI for AnswerGuard's first
public-launch weeks, ordered by failure-mode severity.

Configure each one in PostHog UI under **Insights → New insight → Trend → Save → ⋯ → Manage alerts → New alert**.

The events these alerts depend on were wired in the same PR that added this
doc — see `AnswerGuardScreeningService.kt` and `ProManager.kt`. Before this PR,
`call_screened` and `spam_call_blocked` were declared constants in
`AnalyticsEvents.kt` but were **never emitted** in production code, so any
prior alert configured against them would have been silently dead.

## 1. North-star metric collapse (CRITICAL)

**Signal:** Daily count of `call_screened` events drops below the recent
baseline. Indicates the screening service has stopped firing — Android
revoked the role, the binder is broken, or a release crashed the service.

- Event: `call_screened`
- Aggregation: count, unique users
- Window: 24 hours
- Threshold: < 0.5× (rolling 7-day average)
- Notify: email + Slack
- Severity: P0

## 2. Spam detection ratio collapse (HIGH)

**Signal:** Ratio of `spam_call_blocked` to `call_screened` drops sharply.
Indicates the verdict engine started classifying everything as ALLOW —
a regression in `SpamVerdictEngine` or a corrupted blocklist.

- Formula: `count(spam_call_blocked) / count(call_screened)`
- Window: 6 hours
- Threshold: < 0.5× (rolling 7-day average)
- Severity: P1

## 3. Paywall purchases dying (HIGH)

**Signal:** `paywall_purchase_failed` spikes — Play Billing integration
broke, product IDs got renamed, or a price-tier change misfired.

- Event: `paywall_purchase_failed`
- Aggregation: count
- Window: 1 hour
- Threshold: > 5 per hour
- Severity: P1

Note: a dedicated `paywall_purchase_failed` event was added in this PR
specifically so the alert is a one-line filter rather than a property
filter on `paywall_purchase_result`.

## 4. Onboarding funnel break (MEDIUM)

**Signal:** `first_open` continues but `first_protection_enabled` does not.
Indicates a regression in the role-request flow or permission dialog.

- Formula: `count(first_protection_enabled) / count(first_open)`
- Window: 24 hours
- Threshold: < 0.3 (i.e., fewer than 30% of new installs reach protection)
- Severity: P2

## What's NOT here yet

- **OTel / Log ingestion:** PostHog's `posthog.capture`/OTel logs path
  is JS-only as of 2026-05-29. The Android client doesn't have a Logs API.
  Until it ships, we surface screening failures via the `screening_service_error`
  event taxonomy slot (reserved but not yet wired — add when the next
  screening-path crash class shows up in Crashlytics).
- **Saved views:** Configure in PostHog UI per-account; no repo artifact.
- **Traces (alpha):** Wait until GA before instrumenting.

## Verifying the alert wiring after deploy

```bash
# Build + install on a real device with the CALL_SCREENING role.
make verify
make maestro-android

# Then place a known-spam test call to a number that hits the blocklist.
# Within ~30s the events should appear in PostHog:
#   call_screened (verdict=block)
#   spam_call_blocked
#   first_spam_blocked  (only on the first ever block)
```

If the events don't appear:
1. Confirm `POSTHOG_API_KEY` is non-blank in the installed build's
   `BuildConfig` (CI strips it from PRs without the secret).
2. Confirm AnswerGuard is the active CallScreeningService role.
3. Check Logcat for "screening analytics emit failed" warnings.
