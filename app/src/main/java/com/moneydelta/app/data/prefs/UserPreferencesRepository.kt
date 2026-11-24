package com.moneydelta.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moneydelta.app.domain.BillingCycleCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.moneyDeltaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "money_delta_prefs"
)

data class UserPreferences(
    val currencySymbol: String = "$",
    val currencyCode: String = "USD",
    val cycleStartDay: Int = 1,
    val lastSyncMillis: Long = 0L
)

class UserPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.moneyDeltaDataStore

    private object Keys {
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val CYCLE_START_DAY = intPreferencesKey("cycle_start_day")
        val LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
    }

    val preferencesFlow: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "$",
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "USD",
            cycleStartDay = BillingCycleCalculator.clampCycleStartDay(
                prefs[Keys.CYCLE_START_DAY] ?: 1
            ),
            lastSyncMillis = prefs[Keys.LAST_SYNC_MILLIS] ?: 0L
        )
    }

    suspend fun getPreferencesOnce(): UserPreferences = preferencesFlow.first()

    suspend fun setCurrency(symbol: String, code: String) {
        dataStore.edit { prefs ->
            prefs[Keys.CURRENCY_SYMBOL] = symbol.trim().ifEmpty { "$" }
            prefs[Keys.CURRENCY_CODE] = code.trim().uppercase().ifEmpty { "USD" }
        }
    }

    suspend fun setCycleStartDay(day: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.CYCLE_START_DAY] = BillingCycleCalculator.clampCycleStartDay(day)
        }
    }

    suspend fun setLastSyncMillis(timestampMillis: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_MILLIS] = timestampMillis
        }
    }
}
