package com.moneydelta.app.data

fun interface WidgetRefreshDelegate {
    suspend fun refreshAllWidgets()
}
