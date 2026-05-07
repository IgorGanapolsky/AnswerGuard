package com.igorganapolsky.answerguard.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import java.util.UUID

import android.os.Build
import androidx.annotation.RequiresApi

/**
 * AnswerGuard call screening service.
 *
 * Registered in AndroidManifest with BIND_SCREENING_SERVICE permission.
 * The system calls [onScreenCall] for each incoming call; we must respond
 * within 5 seconds or the system defaults to allowing the call.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AnswerGuardScreeningService : CallScreeningService() {

    private val tag = "AnswerGuardScreening"

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle?.schemeSpecificPart ?: ""
        
        // Extract STIR/SHAKEN verification status (VERSTAT)
        // Available since Android 11 (API 30)
        val verstat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callDetails.callerNumberVerificationStatus
        } else {
            -1 // Unknown
        }

        Log.d(tag, "Screening call from: $handle (VERSTAT: $verstat)")

        val verdict = SpamVerdictEngine.evaluate(handle, verstat)
        Log.i(tag, "Verdict for $handle: $verdict")

        // Record the event for the UI
        ScreeningLog.log(ScreeningEvent(
            id = UUID.randomUUID().toString(),
            phoneNumber = handle,
            verdict = when(verdict) {
                SpamVerdict.BLOCK -> ScreeningVerdict.BLOCKED
                SpamVerdict.SILENCE -> ScreeningVerdict.SILENCED
                SpamVerdict.ALLOW -> ScreeningVerdict.ALLOWED
            },
            reason = "Pattern matching + Local Rules"
        ))

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
