package com.moneydelta.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.moneydelta.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Concatenate multi-part SMS segments grouped by sender
        val groupedBySender = LinkedHashMap<String, Pair<StringBuilder, Long>>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: continue
            val body = sms.displayMessageBody ?: sms.messageBody ?: ""
            val timestamp = sms.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()

            val entry = groupedBySender.getOrPut(sender) {
                StringBuilder() to timestamp
            }
            entry.first.append(body)
        }

        if (groupedBySender.isEmpty()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = AppContainer.get(appContext).ledgerRepository
                for ((sender, pair) in groupedBySender) {
                    val (bodyBuilder, timestamp) = pair
                    repo.ingestSingleSms(
                        sender = sender,
                        body = bodyBuilder.toString(),
                        timestampMillis = timestamp
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
