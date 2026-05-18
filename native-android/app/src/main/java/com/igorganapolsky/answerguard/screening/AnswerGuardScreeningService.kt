package com.igorganapolsky.answerguard.screening

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

        val response = CallResponse.Builder().apply {
            when (verdict) {
                SpamVerdict.BLOCK -> {
                    setRejectCall(true)
                    setDisallowCall(true)
                    setSilenceCall(true)
                    setSkipNotification(true)
                }
                SpamVerdict.SILENCE -> {
                    setRejectCall(false)
                    setDisallowCall(false)
                    setSilenceCall(true)
                    setSkipNotification(false)
                }
                SpamVerdict.ALLOW -> {
                    setRejectCall(false)
                    setDisallowCall(false)
                    setSilenceCall(false)
                }
            }
        }.build()

        respondToCall(callDetails, response)
    }
}
