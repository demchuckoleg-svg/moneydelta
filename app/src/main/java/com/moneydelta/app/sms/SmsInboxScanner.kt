package com.moneydelta.app.sms

import android.content.Context
import android.provider.Telephony
import com.moneydelta.app.data.LedgerRepository
import com.moneydelta.app.domain.model.ParsedSmsTransaction
import com.moneydelta.app.domain.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

data class RawInboxSms(
    val id: Long,
    val sender: String,
    val body: String,
    val timestampMillis: Long
)

data class InboxScanResult(
    val scannedMessages: Int,
    val newlyImportedCount: Int,
    val permissionMissing: Boolean = false
)

class SmsInboxScanner(
    private val context: Context,
    private val ledgerRepository: LedgerRepository
) {

    suspend fun scanLast12Months(): InboxScanResult = withContext(Dispatchers.IO) {
        if (!SmsPermissions.hasReadSms(context)) {
            return@withContext InboxScanResult(
                scannedMessages = 0,
                newlyImportedCount = 0,
                permissionMissing = true
            )
        }

        val prefs = ledgerRepository.getPreferencesOnce()
        val rules = ledgerRepository.getEnabledBankRules()
        val cutoffMillis = ZonedDateTime.now().minusMonths(12).toInstant().toEpochMilli()

        val parsedTransactions = mutableListOf<ParsedSmsTransaction>()
        var scannedCount = 0

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffMillis.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    scannedCount++
                    if (rules.isEmpty()) continue

                    val sender = if (addressIdx >= 0) cursor.getString(addressIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    if (sender.isBlank() || body.isBlank() || timestamp <= 0L) continue

                    val parsed = SmsParser.parse(
                        sender = sender,
                        body = body,
                        timestampMillis = timestamp,
                        rules = rules,
                        primaryCurrencyCode = prefs.currencyCode,
                        primaryCurrencySymbol = prefs.currencySymbol
                    )
                    if (parsed != null) {
                        parsedTransactions += parsed
                    }
                }
            }
        } catch (_: SecurityException) {
            return@withContext InboxScanResult(
                scannedMessages = 0,
                newlyImportedCount = 0,
                permissionMissing = true
            )
        }

        val inserted = ledgerRepository.ingestParsedTransactions(
            parsed = parsedTransactions,
            updateSyncTimestamp = true
        )

        InboxScanResult(
            scannedMessages = scannedCount,
            newlyImportedCount = inserted,
            permissionMissing = false
        )
    }

    suspend fun readRecentInboxMessages(limit: Int = 25): List<RawInboxSms> =
        withContext(Dispatchers.IO) {
            if (!SmsPermissions.hasReadSms(context)) return@withContext emptyList()

            val results = mutableListOf<RawInboxSms>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val sortOrder = "${Telephony.Sms.DATE} DESC"

            try {
                context.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                    val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                    while (cursor.moveToNext() && results.size < limit) {
                        val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                        val sender = if (addressIdx >= 0) cursor.getString(addressIdx).orEmpty() else ""
                        val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                        val timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                        if (sender.isNotBlank() && body.isNotBlank()) {
                            results += RawInboxSms(
                                id = id,
                                sender = sender,
                                body = body,
                                timestampMillis = timestamp
                            )
                        }
                    }
                }
            } catch (_: SecurityException) {
                return@withContext emptyList()
            }

            results
        }
}
