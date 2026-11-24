package com.moneydelta.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneydelta.app.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["sourceHash"], unique = true),
        Index(value = ["timestampMillis"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amountCents: Long,
    val type: TransactionType,
    val timestampMillis: Long,
    val sender: String,
    val description: String,
    val rawBody: String? = null,
    val isIgnored: Boolean = false,
    val isManual: Boolean = false,
    val ruleId: Long? = null,
    val sourceHash: String? = null
)
