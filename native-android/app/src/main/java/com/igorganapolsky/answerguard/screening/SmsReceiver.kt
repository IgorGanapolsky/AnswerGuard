package com.igorganapolsky.answerguard.screening

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.igorganapolsky.answerguard.billing.ProManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    private val tag = "AnswerGuardSmsReceiver"

    @Inject
    lateinit var proManager: ProManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(tag, "SMS received broadcast triggered")

        // SMS identification is a premium Pro subscription feature
        val isPro = proManager.isPro.value
        if (!isPro) {
            Log.d(tag, "SMS received but user is not Pro — skipping offline caller identification")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            Log.i(tag, "SMS from: $sender")

            // Identify sender locally using zero-knowledge Caller ID database
            val senderName = CallerIdDatabase.identify(sender)

            // Record in ScreeningLog so it shows up in Recent Activity
            ScreeningLog.record(
                ScreenedCall(
                    number = sender,
                    verdict = SpamVerdict.ALLOW,
                    timestamp = System.currentTimeMillis(),
                    callType = "SMS",
                    senderName = senderName
                )
            )

            // Record a High-Value Action for analytics/monetization
            proManager.recordHighValueAction("sms_protection")
        }
    }
}
