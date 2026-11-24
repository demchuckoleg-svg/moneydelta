package com.moneydelta.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BankRuleDao {

    @Query("SELECT * FROM bank_rules ORDER BY id ASC")
    fun observeAll(): Flow<List<BankRuleEntity>>

    @Query("SELECT * FROM bank_rules ORDER BY id ASC")
    suspend fun getAllOnce(): List<BankRuleEntity>

    @Query("SELECT * FROM bank_rules WHERE isEnabled = 1 ORDER BY id ASC")
    suspend fun getEnabledOnce(): List<BankRuleEntity>

    @Upsert
    suspend fun upsert(rule: BankRuleEntity): Long

    @Query("UPDATE bank_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM bank_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
