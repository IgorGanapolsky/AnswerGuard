# AnswerGuard — Play Console "App content" Answer Pack

Generated from a source-code audit on 2026-05-21. For each Play Console **App content**
section: the recommended answer, the rationale, and what only **you** must decide/provide.

> ⚠️ **Read before submitting.** Data Safety and content declarations are legal
> statements Google holds the developer accountable for. Everything below is
> derived from the code, but **you must verify it** — especially anything marked
> `[VERIFY]` or `[YOU PROVIDE]` — before submitting in the Console.

---

## Audited data practices (what the code actually does)

| Area | Finding |
|---|---|
| Call screening | 100% on-device (`SpamVerdictEngine`, `AnswerGuardScreeningService`) |
| Screened-call log | Local only (`ScreeningLog` → SharedPreferences). Never transmitted |
| User blocklist | Local only (`UserBlocklist` → SharedPreferences). Never transmitted |
| Contacts (`READ_CONTACTS`) | Queried **on-device only** (`ContactsAllowlist` via `ContactsContract.PhoneLookup`). Never transmitted |
| Phone numbers | **Never sent off-device.** Block/unblock analytics events send only `number_length` (a digit count) |
| Analytics — PostHog | Active. Events tied to a random app-generated UUID (`distinct_id`), not a hardware/advertising ID |
| Firebase Analytics | Bundled but **event collection explicitly disabled** at startup |
| Firebase Crashlytics | **Active in release builds** (plugin applies when `google-services.json` is injected via CI) — collects crash logs/diagnostics |
| Sentry | SDK bundled but **never initialized** — collects nothing |
| Google Play Billing | Pro subscription; Google processes payments |
| Advertising ID | `AD_ID` permission **removed** — not collected; no ads |

---

## 1. Privacy policy — `[YOU PROVIDE a URL]`

Required. You must host a privacy policy and enter its URL. The GitHub Pages site
(`igorganapolsky.github.io/AnswerGuard`) can host it. It must disclose: on-device
contact access, on-device call screening, PostHog analytics, Firebase Crashlytics,
and Play Billing. **Action: write + host the policy, then paste the URL.**

## 2. App access

**Recommended: "All functionality is available without special access."**
The app has no login/account. AnswerGuard Pro is an in-app purchase, not an access
credential, so reviewers need nothing special to test all features.

## 3. Ads

**Recommended: "No, my app does not contain ads."**
No ads SDK is present and the `AD_ID` permission is explicitly removed in the manifest.

## 4. Content rating (IARC questionnaire)

Category: **Utility / Communication**. Answer **No** to all mature-content questions
(violence, sexual content, profanity, controlled substances, gambling, user-generated
content, etc.). Expected result: **Everyone**. `[VERIFY]` each answer as you go.

## 5. Target audience and content — `[YOU DECIDE]`

Recommended target age band: **18+** (or 13+). AnswerGuard is a utility, **not**
directed at or appealing to children. Answer **No** to "is your app designed for
children." Do not enrol in the Designed-for-Families program.

## 6. Data safety — `[VERIFY before submitting]`

**Data collected** (transmitted off device):

| Data type | Collected? | Purpose | Notes |
|---|---|---|---|
| App interactions / activity | Yes | Analytics | PostHog usage/funnel events |
| Crash logs | Yes | Analytics, app functionality | Firebase Crashlytics (release builds) |
| Diagnostics | Yes | Analytics, app functionality | Crashlytics performance data |
| Device or other IDs | Yes `[VERIFY]` | Analytics | Random app-generated `distinct_id` (not advertising ID) |
| Purchase history | Yes `[VERIFY]` | App functionality | Pro entitlement / product IDs |

**Data NOT collected — do NOT declare** (processed on-device only, never transmitted):
Contacts · Phone numbers · Call log · Location.

**Security / handling answers:**
- Encrypted in transit: **Yes** (PostHog + Crashlytics over HTTPS).
- Data deletion: there is currently **no in-app "delete my data" flow** — see Open Items.
- Is collection required: currently **yes, required** (no in-app analytics opt-out).

## 7. Government apps / Financial features / Health

All three: **No.** AnswerGuard is a consumer call-screening utility.

## 8. Sensitive permission declaration — `READ_CONTACTS`

Play requires a Permissions Declaration. Recommended justification:

> AnswerGuard reads contacts solely on-device to recognise the user's known
> contacts so that legitimate callers are never screened, silenced, or blocked.
> Contact data is never transmitted off the device or shared. The feature is core
> to the app's call-screening function.

The app deliberately does **not** request `READ_CALL_LOG` — the compliant design for
a call-screening app.

---

## Open items / recommendations

1. **Crashlytics vs. PostHog overlap.** The code disables Firebase *Analytics* ("PostHog
   is our source of truth") but leaves *Crashlytics* active. Either keep it (and declare
   Crash logs + Diagnostics, as above) or disable it to simplify the Data Safety form.
2. **No data-deletion path.** Consider adding an in-app "reset analytics / delete my
   data" action (`AnalyticsService.reset()` already exists) and a deletion URL — Google
   asks for this in Data Safety.
3. **Privacy policy** must be written and hosted before submission (section 1).
