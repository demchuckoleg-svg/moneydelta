package com.moneydelta.app.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneydelta.app.di.AppContainer
import java.util.concurrent.TimeUnit

class SmsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scanner = AppContainer.get(applicationContext).smsInboxScanner
        scanner.scanLast12Months()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "money_delta_sms_sync"

        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SmsSyncWorker>(
                repeatInterval = 3,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
