package com.igorganapolsky.answerguard.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
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

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle?.schemeSpecificPart ?: ""
        Log.d(tag, "Screening call from: $handle")

        if (PauseState.isPaused(this)) {
            Log.i(tag, "Paused — allowing all calls through")
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
