package com.igorganapolsky.answerguard.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.igorganapolsky.answerguard.analytics.AnalyticsEvents
import com.igorganapolsky.answerguard.analytics.AnalyticsService
import com.igorganapolsky.answerguard.billing.ProManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * AnswerGuard call screening service.
 *
 * Registered in AndroidManifest with BIND_SCREENING_SERVICE permission.
 * The system calls [onScreenCall] for each incoming call; we must respond
 * within 5 seconds or the system defaults to allowing the call.
 */
@AndroidEntryPoint
class AnswerGuardScreeningService : CallScreeningService() {

    private val tag = "AnswerGuardScreening"

    @Inject
    lateinit var proManager: ProManager

    @Inject
    lateinit var analyticsService: AnalyticsService

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle?.schemeSpecificPart ?: ""
        Log.d(tag, "Screening call from: $handle")

        if (PauseState.isPaused(this)) {
            Log.i(tag, "Paused — allowing all calls through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // VoIP bypass: WhatsApp, Signal, FaceTime, Zoom etc. register as
        // self-managed ConnectionServices, and Android still routes them
        // through CallScreeningService. Their handles aren't phone numbers
        // (often literal strings like "WhatsApp Call" or app-specific JIDs),
        // so SpamVerdictEngine.evaluate sees `digits.isBlank()` and silences
        // them as "private numbers" — exactly the bug a user just reported.
        //
        // Two signals to detect VoIP, both checked because either alone has
        // false-negative gaps on some OEM ROMs:
        //   1. callDetails.callProperties & PROPERTY_SELF_MANAGED — the
        //      official Android flag set by ConnectionService.setSelfManaged.
        //   2. The handle URI scheme — cellular calls are "tel:"; VoIP can be
        //      "sip:", a custom app scheme, or null.
        val isSelfManaged = callDetails.callProperties and
            Call.Details.PROPERTY_SELF_MANAGED != 0
        val scheme = callDetails.handle?.scheme
        if (isSelfManaged || (scheme != null && scheme != "tel")) {
            Log.i(
                tag,
                "Non-cellular call (selfManaged=$isSelfManaged scheme=$scheme) — " +
                    "passing through without screening",
            )
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val verdict = SpamVerdictEngine.evaluate(this, handle)
        Log.i(tag, "Verdict for $handle: $verdict")

        // Record the screened call in local history
        ScreeningLog.record(ScreenedCall(number = handle, verdict = verdict))

        // June 2026: Record High-Value Actions for dynamic monetization
        if (verdict == SpamVerdict.BLOCK || verdict == SpamVerdict.SILENCE) {
            proManager.recordHighValueAction("ai_protection")
        }

        // PostHog telemetry. CallScreeningService has a 5s SLA from the OS,
        // so wrap every analytics emit in runCatching — we'd rather drop a
        // metric than crash inside the hot path.
        runCatching {
            analyticsService.track(
                AnalyticsEvents.CALL_SCREENED,
                mapOf("verdict" to verdict.name.lowercase()),
            )
            when (verdict) {
                SpamVerdict.BLOCK -> {
                    analyticsService.track(AnalyticsEvents.SPAM_CALL_BLOCKED)
                    analyticsService.trackFirstSpamBlockedIfNeeded()
                }
                SpamVerdict.SILENCE -> analyticsService.track(AnalyticsEvents.SPAM_CALL_SILENCED)
                SpamVerdict.ALLOW -> Unit
            }
        }.onFailure { Log.w(tag, "screening analytics emit failed", it) }

        // setSilenceCall is API 29+. Service only binds via ROLE_CALL_SCREENING
        // (API 29+) in practice, but guard defensively so a legacy binder on
        // 26-28 degrades gracefully instead of crashing with NoSuchMethodError.
        val canSilence = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val response = CallResponse.Builder().apply {
            when (verdict) {
                SpamVerdict.BLOCK -> {
                    setRejectCall(true)
                    setDisallowCall(true)
                    if (canSilence) setSilenceCall(true)
                    setSkipNotification(true)
                }
                SpamVerdict.SILENCE -> {
                    setRejectCall(false)
                    setDisallowCall(false)
                    if (canSilence) setSilenceCall(true)
                    setSkipNotification(false)
                }
                SpamVerdict.ALLOW -> {
                    setRejectCall(false)
                    setDisallowCall(false)
                    if (canSilence) setSilenceCall(false)
                }
            }
        }.build()

        respondToCall(callDetails, response)
    }
}
