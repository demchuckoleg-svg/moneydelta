package com.moneydelta.app.di

import android.content.Context
import com.moneydelta.app.data.LedgerRepository
import com.moneydelta.app.data.WidgetRefreshDelegate
import com.moneydelta.app.data.db.AppDatabase
import com.moneydelta.app.data.prefs.UserPreferencesRepository
import com.moneydelta.app.sms.SmsInboxScanner

class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(
            transactionDao = database.transactionDao(),
            bankRuleDao = database.bankRuleDao(),
            preferencesRepository = preferencesRepository
        )
    }

    val smsInboxScanner: SmsInboxScanner by lazy {
        SmsInboxScanner(
            context = appContext,
            ledgerRepository = ledgerRepository
        )
    }

    fun registerWidgetRefreshDelegate(delegate: WidgetRefreshDelegate) {
        ledgerRepository.setWidgetRefreshDelegate(delegate)
    }

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
