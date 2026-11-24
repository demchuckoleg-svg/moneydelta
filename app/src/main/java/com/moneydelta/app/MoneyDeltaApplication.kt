package com.moneydelta.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.moneydelta.app.data.WidgetRefreshDelegate
import com.moneydelta.app.di.AppContainer
import com.moneydelta.app.sms.SmsSyncWorker
import com.moneydelta.app.widget.MoneyDeltaWidget

class MoneyDeltaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer.get(this)
        container.registerWidgetRefreshDelegate(
            WidgetRefreshDelegate {
                MoneyDeltaWidget().updateAll(this@MoneyDeltaApplication)
            }
        )
        SmsSyncWorker.schedulePeriodicSync(this)
    }
}
