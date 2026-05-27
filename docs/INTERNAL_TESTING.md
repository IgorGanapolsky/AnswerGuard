# AnswerGuard — Internal Testing Guide

## TL;DR

**Stop relying on Firebase App Distribution for AnswerGuard internal builds.
Move internal testing to the Play Console Internal Testing track.**

## Why

The internal-distribution workflow has been "succeeding" (uploads to Firebase
work, distribution to testers/groups returns OK) but **testers can't actually
install the resulting APK** because Google Play Protect classifies it as
harmful at install time. This is documented at
[`internal-distribution.yml:577`](../.github/workflows/internal-distribution.yml).

The cause is well-understood: AnswerGuard requests `RECEIVE_SMS`,
`BIND_SCREENING_SERVICE`, `READ_CALL_LOG`, and (as of v1.2.8) `READ_VOICEMAIL`.
For a sideloaded APK, this permission cocktail trips Play Protect's high-risk
classifier — even when signed with the production keystore. Per the 2026
research:

> Trust accrues to the signing key's Play Console history. But Play Protect
> still inspects install *source* — APKs delivered via FAD/browser/direct
> link are treated as internet-sideloads regardless of signature. Apps
> become Play-Protect-trusted only once they have successfully published
> to at least one Play Console test track.

(See [Developer Guidance for Google Play Protect Warnings](https://developers.google.com/android/play-protect/warning-dev-guidance).)

## What to do instead

### Use Play Console Internal Testing track

Play Console → AnswerGuard → Testing → **Internal testing**.

- Up to **100 internal testers** per Google account.
- No Google review delay (releases go live within minutes of upload).
- Testers install via a Play Store opt-in URL, so Play Protect treats the
  install as first-party trusted. No "harmful app" warning.
- You'll need this track approved for production launch *anyway* — the audit
  flagged "closed-testing 12-tester / 14-day requirement for new personal
  developer accounts" as a hard blocker.

### One-time setup

1. **Play Console UI**:
   - Go to *Testing → Internal testing*.
   - Click *Create new release*.
   - Upload the AAB produced by `internal-distribution.yml`'s
     `android-play-internal` job (it already exists — that part of the
     workflow has been succeeding all along; we just weren't using its
     output for tester install).
   - Add testers (email addresses or a Google Group). The repo already
     has these stored as the `FIREBASE_INTERNAL_TESTERS` secret —
     reuse the list.
   - Click *Release*.
2. **Share the opt-in URL** Play Console generates with each tester. They
   tap it, accept the test, then install AnswerGuard from the Play Store
   like any other app. **Play Protect does not warn.**

### CI changes (next sprint)

The `internal-distribution.yml` workflow's `Android Google Play (Internal)`
job already uploads to the Play internal track on every push to develop.
What's missing is a step to **promote that upload to the internal-testing
release** automatically. The Google Play Android Publisher API supports
this; see `scripts/play_data_safety_upload.py` for the auth pattern.

Until that automation lands, the workflow's outputs are usable manually:
each successful run leaves an AAB at the internal track that you can
promote in the Console UI in two clicks.

## When you still want Firebase App Distribution

Keep FAD for **debug-signed** APKs only — QA builds that don't represent
what ships to users. Set the workflow `target=android_play` (or
`target=all_safe`) to skip the FAD distribution step for release-signed
APKs:

```bash
gh workflow run internal-distribution.yml --field target=android_play
```

This is documented in the workflow's `target` input options at
`internal-distribution.yml:17-26`.

## Filing a Play Protect appeal (slower fallback)

If for some reason you need the Firebase APK channel to work too
(emergency hotfix dogfooding, etc.):

> https://support.google.com/googleplay/android-developer/contact/protectappeals

Documented to work for enterprise/DPC apps. Expect days, not minutes.

## What does NOT work — debunked theories

The research explicitly ruled out:

- **"Once we ship to Play Store the FAD APK will be trusted too."** No
  documented automatic package+signature whitelist for sideloads from
  other channels. Multiple shipped Play apps still get blocked when
  re-downloaded from other sources.
- **Signing with the upload key only.** Same Play Protect classifier;
  if anything worse (upload key has even less reputation than Play App
  Signing key).
- **Internal App Sharing (IAS) as a stealth channel.** Google explicitly
  states "protection is not applied" for IAS — but Play Protect *itself*
  still scans installs.
- **Disabling Play Protect on tester devices** as a scalable workflow.
  Works on one device; doesn't scale to 10 testers, and Android 15+ has
  begun ignoring the toggle for high-risk permission cocktails.

## References

- [Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Set up internal testing](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Permissions Declaration policy (SMS / Call Log)](https://support.google.com/googleplay/android-developer/answer/10208820)
- Repo workflow: `.github/workflows/internal-distribution.yml`
- Repo guide: `docs/STORE_READINESS.md`
