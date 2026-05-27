# AnswerGuard — RECEIVE_SMS Permissions Declaration Template

`AndroidManifest.xml` declares `android.permission.RECEIVE_SMS` to support
the Pro-tier **SMS caller identification** feature (`SmsReceiver.kt`). Since
AnswerGuard does **not** register as Android's default SMS handler, Google
Play requires a Permissions Declaration before any release that includes
this permission is accepted. The form lives in:

> Play Console → App content → SMS or Call Log permissions →
> *Add SMS or Call Log permissions declaration*

Copy each section below verbatim into the corresponding Console field.

---

## 1. Core use case

**Use case:** *Other (please specify)*

**Explain how your app uses this permission:**

> AnswerGuard is a call-screening and caller-ID utility for Android.
> The Pro, Family, and Business tiers include a feature called "SMS caller
> identification" that enriches the caller-ID display for business-class
> senders (delivery notifications, two-factor authentication codes,
> appointment reminders, retail outreach).
>
> `RECEIVE_SMS` is requested at runtime — only after the user opts into a
> paid tier and explicitly grants the permission via the standard Android
> runtime dialog. The receiver (`SmsReceiver.kt`) reads the sender address
> and message body on-device only, extracts business-name identifiers, and
> stores the result in the local caller-ID database. The contents of SMS
> messages are never transmitted off-device, never logged to crash or
> analytics services, and never retained beyond the lifetime of a single
> caller-ID enrichment lookup.
>
> The app is not a messaging app and does not display, store, forward,
> redirect, or summarise SMS content. It does not request the default
> SMS handler role.

---

## 2. Feature explanation (user-visible)

**What feature requires this permission?**

> Pro / Family / Business "SMS caller identification" — when an incoming
> SMS arrives from a number not in the user's contacts, the app extracts
> the business name (when present in the SMS body) and stores it locally so
> that a subsequent call from the same number displays the recognised
> sender name in the AnswerGuard activity log.

**Why is this feature core to the app's functionality?**

> Caller identification is AnswerGuard's primary purpose. The free tier
> identifies callers using on-device heuristics; the paid tiers add SMS-
> derived identification because business senders frequently text from the
> same numbers they call, and the SMS body often contains the canonical
> business name. Without `RECEIVE_SMS`, paid users cannot get the enriched
> caller-ID experience they paid for.

---

## 3. Alternative-API justification

**Have you considered using an alternative API that does not require
this permission?**

> Yes. Google's `Phone.RoleCallScreening` API (used by the free tier) does
> not provide SMS-derived metadata. The `RoleManager.ROLE_SMS` default-
> handler role is overkill — it would replace Google Messages on the user's
> device, which is a far larger trust ask than reading SMS for caller-ID.
> `READ_PHONE_NUMBERS` does not surface SMS sender metadata. There is no
> Android API that exposes business-name strings from incoming SMS without
> `RECEIVE_SMS`.

---

## 4. Data handling

**Is SMS data shared with third parties?** No.

**Is SMS data uploaded to any backend?** No.

**Is SMS data encrypted at rest?** SMS data is not retained at rest —
the enriched caller-ID record stored locally contains only the resolved
business name string (e.g. "ACME Pharmacy"), never the SMS body.

**Data retention:** Resolved business-name strings are stored in the
local caller-ID database until the user invokes the in-app
*Settings → Privacy & Data → Delete my data* action (introduced in
v1.2.8 — PR #111) or uninstalls the app.

---

## 5. Privacy policy references

The privacy policy at
`https://igorganapolsky.github.io/AnswerGuard/privacy.html` explicitly
discloses SMS reception under **"Information processed only on your
device"** (PR #110, effective 2026-05-27). Cite this URL in the
declaration form.

---

## 6. Demo video

The Permissions Declaration form asks for a YouTube link demonstrating
the feature. Either:

- **Easier:** record a short (under 60 seconds) screen capture from a
  Pixel running an internal AnswerGuard build:
  1. Open Pixel Messages and send the test phone the SMS `"Your ACME Pharmacy prescription is ready"` from another number.
  2. Switch to AnswerGuard → wait a beat → the activity row updates to show "ACME Pharmacy" as the sender.
  3. From the second phone, call the test phone → AnswerGuard's screening verdict notification shows "ACME Pharmacy" as the caller name.
  Upload as Unlisted to YouTube.
- **If you don't have two phones handy:** Maestro flow can mock this —
  follow up with a `make demo-video-sms` target. Not yet built.

---

## 7. Audit trail

Every claim in this declaration is verifiable against the AnswerGuard
source at https://github.com/IgorGanapolsky/AnswerGuard:

| Claim | Source of truth |
|---|---|
| RECEIVE_SMS only requested at runtime after Pro purchase | `native-android/app/src/main/java/com/igorganapolsky/answerguard/MainActivity.kt` `smsPermissionLauncher`, gated by tier |
| Receiver does not transmit SMS off-device | `native-android/app/src/main/java/com/igorganapolsky/answerguard/screening/SmsReceiver.kt` — no network calls, only `CallerIdDatabase.add(...)` |
| Body is not stored | `SmsReceiver.kt` extracts business-name regex match only |
| Not a default SMS handler | `AndroidManifest.xml` declares no `<intent-filter>` for `android.provider.Telephony.SMS_DELIVER` |

---

*If the declaration is rejected, the alternative is to remove
`SmsReceiver.kt` and the `RECEIVE_SMS` permission entirely, then update
the paywall copy in PR #113 to drop "SMS caller identification" from
the Pro feature list. This would lose a Pro selling point but unblocks
the listing immediately.*
