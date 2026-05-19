package com.igorganapolsky.answerguard.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * AnswerGuard call screening service.
 *
 * Registered in AndroidManifest with BIND_SCREENING_SERVICE permission.
 * The system calls [onScreenCall] for each incoming call; we must respond
 * within 5 seconds or the system defaults to allowing the call.
 */
class AnswerGuardScreeningService : CallScreeningService() {

    private val tag = "AnswerGuardScreening"

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle?.schemeSpecificPart ?: ""
        Log.d(tag, "Screening call from: $handle")

        val verdict = SpamVerdictEngine.evaluate(this, handle)
        Log.i(tag, "Verdict for $handle: $verdict")

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
