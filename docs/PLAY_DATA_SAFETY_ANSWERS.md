# AnswerGuard — Play Data Safety Answer Key

Use this to fill out the **App content → Data safety** form in Play Console
(under `ig5973700@gmail.com`). Every answer here is derived from a real audit
of `native-android/app/src/main/`. Verify each line yourself before submitting
— Google holds the developer accountable.

The Console form is identical to the empty CSV at
`marketing/compliance/play_data_safety.csv`. After you fill it in the
Console UI, export to CSV and either commit it or set as
`PLAY_DATA_SAFETY_CSV` secret so `play-data-safety-sync.yml` can re-upload
on every release.

---

## Section 1 — Top-level questions

| Question | Answer | Rationale |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | PostHog + Firebase Crashlytics collect anonymous diagnostics. Play Billing handles purchases. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | PostHog (https://posthog.com/), Firebase Crashlytics, and Play Billing are all HTTPS-only. |
| Do you provide a way for users to request that their data is deleted? | **Yes** | Two paths: in-app `Privacy & Data → Delete my data` (ships in v1.2.8 — PR #111), and email request via `iganapolsky@gmail.com` forwarded to PostHog + Firebase processors within 30 days. Both documented in [`docs/privacy.html`](privacy.html). |

---

## Section 2 — Data types **collected** (declare each as Collected = Yes)

For each row: **Collected = Yes**; **Shared = No** (PostHog and Crashlytics
are service-providers acting on the developer's behalf, not third-party
recipients — see [Play SDK index data sharing definitions](https://support.google.com/googleplay/android-developer/answer/10787469));
**Processed Ephemerally = No** (data is persisted by the service providers);
**Required vs Optional = User can choose** (users without analytics opt-out
flow today get Optional anyway; safer floor).

### Personal info → **NONE COLLECTED**
- Name, Email address, User IDs, Address, Phone number, Political/religious beliefs, Sexual orientation, Other info → all **No**.
  - The app has no account, no login, no name field, no email field.
  - `distinct_id` in PostHog is a random UUID, **not** a User ID per Play's definition (not tied to a Google account, email, or device identifier).

### Financial info → declare only **Purchase history**
- **Purchase history = Yes** — Play Billing entitlement (`com.igorganapolsky.answerguard.billing.ProManager` queries `INAPP` + `SUBS` purchase histories). Purpose: **App functionality**. Required for the Pro/Family/Business tiers to work.
- User payment info, Credit score, Other financial info → **No**.
  - Payment instrument is processed by Google Play; the app never sees a card number, bank account, or billing address.

### Health and fitness → **NONE**
- Health info, Fitness info → **No**.

### Messages → **NONE COLLECTED (off-device)**
- Emails, SMS or MMS messages, Other in-app messages → **No** in the *collected* sense (declare these only if the data leaves the device).
  - `SmsReceiver.kt` reads incoming SMS bodies on-device for Pro caller-ID enrichment. **Nothing is uploaded.** Play's definition of "collected" requires off-device transfer.
  - **If the Console UI asks you to declare on-device-only SMS reading regardless, declare it as Optional and the purpose as "App functionality"**. Otherwise leave it No.

### Photos and videos → **NONE**
- Photos, Videos → **No**.

### Audio files → **NONE**
- Voice or sound recordings, Music files, Other audio files → **No**.
  - The app does not record, process, or upload audio. The Roadmap claim around "voice biometrics" is explicitly marked unbuilt in `full_description.txt` after PR #112.

### Files and docs → **NONE**
- Files and docs → **No**.

### Calendar → **NONE**
- Calendar events → **No**.

### Contacts → **NONE COLLECTED (off-device)**
- Contacts → **No** in the collected sense.
  - `READ_CONTACTS` is requested but contact lookup happens entirely on-device via `ContactsContract.PhoneLookup` in `ContactsAllowlist.kt`. Numbers never leave the device.
  - Same on-device-only carve-out as SMS.

### App activity → declare **Page views and taps in app**, **Other actions**, **Other user-generated content**
- **Page views and taps in app = Yes** — PostHog events tracked from `AnalyticsService.track(...)`. Examples: `app_opened`, `call_screening_enabled`, `paywall_viewed`. Purpose: **Analytics**. Required = Optional.
- **Other actions = Yes** — block/unblock actions emit anonymous events with `number_length` (digit count) only, never the number itself.
- **Other user-generated content = No** — blocklist entries stay on-device.
- In-app search history, Installed apps, Web browsing → **No**.

### Web browsing → **NONE**
- Web browsing history → **No**.

### App info and performance → declare both
- **Crash logs = Yes** — Firebase Crashlytics (`AnswerGuardApp.onCreate` enables Crashlytics in release builds when `google-services.json` is present). Purpose: **App functionality / Analytics**. Required = Optional.
- **Diagnostics = Yes** — Crashlytics breadcrumbs + PostHog device/OS metadata. Purpose: **Analytics**. Required = Optional.
- Other app performance data → **No**.

### Device or other IDs → declare **Device or other IDs**
- **Device or other IDs = Yes** — PostHog `distinct_id` (random UUID generated at install) and Firebase installation ID. Purpose: **Analytics**. Required = Optional.
- The Android Advertising ID (`AD_ID`) permission is **explicitly removed** via `<uses-permission ... tools:node="remove"/>` in [`AndroidManifest.xml`](../native-android/app/src/main/AndroidManifest.xml). Confirm "No advertising ID is collected" in the Console.

---

## Section 3 — Data types **shared** (with third parties)

**Declare every row as Shared = No.** Justification:

Per Google's policy ([How to fill in the Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469#zippy=%2Cwhat-counts-as-sharing-data)):
> "Sharing refers to transferring user data collected from your app to a third party. **Service providers acting on behalf of the developer don't count as sharing**, provided they meet specific requirements."

PostHog and Firebase Crashlytics are configured as **data processors / service providers**, not third-party recipients:
- PostHog: contract is on the standard processor agreement; data is used solely to provide product analytics to AnswerGuard.
- Firebase Crashlytics: governed by Google's standard processor terms.

Neither is sold to third parties, used for ad targeting, or combined with other datasets. Therefore: **Shared = No** for every category.

If Google's reviewer pushes back: send them the PostHog [Data Processing Agreement](https://posthog.com/dpa) and the [Firebase Data Processing Terms](https://firebase.google.com/terms/data-processing-terms).

---

## Section 4 — Security practices

- **Data encrypted in transit** = Yes (all SDKs HTTPS).
- **Can users request data deletion** = Yes (see top of doc).
- **Follows Play Families Policy** = N/A — declare "App is not directed to children" in Target audience.
- **Independent security review** = No (declare honestly; this is fine for a v1).

---

## Section 5 — Submission checklist

Before you click **Submit** in Play Console:

- [ ] Privacy policy URL `https://igorganapolsky.github.io/AnswerGuard/privacy.html` pasted into **App content → Privacy policy**.
- [ ] **App content → Data safety** filled out per Sections 1-4 above. Hit **Save** then **Submit**.
- [ ] **App content → App access** → "All functionality is available without special access".
- [ ] **App content → Ads** → "No, my app does not contain ads".
- [ ] **App content → Content rating** → completed IARC questionnaire (Utility / Communication, all mature questions = No, expected rating: Everyone).
- [ ] **App content → Target audience and content** → Target age 18+ (or 13+); not directed to children.
- [ ] **App content → Permission declarations** → `READ_CONTACTS` declaration filed (text in [`PLAY_STORE_APP_CONTENT.md`](PLAY_STORE_APP_CONTENT.md)).
- [ ] If keeping `RECEIVE_SMS`: file the SMS Permissions Declaration with the justification "On-device SMS caller-identification for Pro tier; messages are never transmitted off-device. No default-SMS-handler role is requested." Otherwise remove `SmsReceiver` first and clear the manifest line.
- [ ] Export the resulting Data Safety form as CSV and either commit to `marketing/compliance/play_data_safety.csv` or set the `PLAY_DATA_SAFETY_CSV` secret — the `play-data-safety-sync.yml` workflow can then re-upload on every release.

---

## Why this matters right now

The repeated `play-promote-to-production.yml` failures (`HttpError 400
"Precondition check failed"`) since 2026-05-20 are caused by an unfilled
Data Safety form on the Play Console side. Once you submit the form per
this doc, the next production-promote should accept the rollout.

The privacy.html update (PR #110) and the in-app Delete-My-Data flow
(PR #111) make Sections 1 and 4 of this doc factually true on the
device side. Until both PRs merge, **answer "Yes" to "do you provide a
way for users to request that their data is deleted" only after v1.2.8
ships** — the email-only path counts for now, but the in-app button is
what makes Play reviewers comfortable.

---

*Last audit: 2026-05-27. Re-derive when manifest permissions or analytics
SDKs change. The script header in `scripts/play_data_safety_upload.py`
is the source of truth for what the app actually does.*
