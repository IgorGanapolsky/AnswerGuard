# Data Safety Form — Browser-Automation Playbook

Companion to [PLAY_DATA_SAFETY_ANSWERS.md](PLAY_DATA_SAFETY_ANSWERS.md). This is
the **exact UI click-path** for driving the Play Console Data Safety form for
AnswerGuard. Compiled from agent research (May 2026) against the official
[Play Console help page](https://support.google.com/googleplay/android-developer/answer/10787469).

## Stages (no literal "Step N of M" — Play uses a left-rail nav)

1. **Overview** (intro / Start)
2. **Data collection and security**
3. **Data types** (tick the categories that apply)
4. **Data usage and handling** (one sub-screen per ticked sub-type — auto-pruned)
5. **Store listing preview**
6. **Submit**

## Stage 2 — Data collection and security

| Question | AnswerGuard answer |
|---|---|
| Does your app collect or share any required user data types? | **Yes** |
| Is all collected data encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **Yes** (only after PR #111's in-app Delete-My-Data + PR #110's privacy.html updates have shipped to v1.2.8; until then, only the email deletion path is live — answer Yes with that as the documented mechanism) |

Click **Next**.

## Stage 3 — Tick exactly these 6 sub-types

- **Financial info** → Purchase history
- **App activity** → Page views and taps in app, Other actions
- **App info and performance** → Crash logs, Diagnostics
- **Device or other IDs** → Device or other IDs

Leave every other sub-type unticked. Click **Next**.

> **Do NOT tick** "User IDs" under Personal info — PostHog's `distinct_id` is
> a random UUID, not a Play-defined User ID. Forum reviewers have flagged this.

## Stage 4 — Per-type sub-screens (in order)

For each, **Shared = No** (PostHog + Firebase Crashlytics are service providers
per Play's policy, not third-party recipients; the distinction lives only in
your DPAs, not the UI).

| Sub-type | Collected | Purposes | Required |
|---|---|---|---|
| Purchase history | Yes | App functionality | Required |
| Page views and taps in app | Yes | Analytics | Users can choose |
| Other actions | Yes | Analytics | Users can choose |
| Crash logs | Yes | App functionality, Analytics | Users can choose |
| Diagnostics | Yes | Analytics | Users can choose |
| Device or other IDs | Yes | Analytics | Users can choose |

For each sub-screen: tick the boxes above, click **Next**.

## Stage 5 — Store listing preview

Visually diff against this doc. **PAUSE & ask user to eyeball** before committing.

## Stage 6 — Final

Click **Submit**. Confirm the "Need attention" pill disappears from the App
Content dashboard.

## Gotchas

- Submit refuses until **App content → Privacy policy** URL is set ✅ (done in this session)
- Submit refuses until **Permission declarations** (READ_CONTACTS, RECEIVE_SMS) are filed — `READ_CONTACTS` is straightforward; `RECEIVE_SMS` needs the video upload template at [PLAY_SMS_DECLARATION.md](PLAY_SMS_DECLARATION.md).
- "Save as draft" preserves but does NOT clear the orange "Needs attention" badge.
- After Submit, the form locks for ~5 min before a new version can be edited.
- No Ad ID question appears because `AD_ID` is stripped via `tools:node="remove"` — confirm in the next AAB upload.

## Buttons
- **Next** advances
- **Save as draft** persists but does not clear "Needs attention"
- **Discard changes** reverts the current sub-screen
- **Submit** at Stage 6 is the only button that commits

## Sources
- [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- [What counts as sharing data](https://support.google.com/googleplay/android-developer/answer/10787469#zippy=%2Cwhat-counts-as-sharing-data)
