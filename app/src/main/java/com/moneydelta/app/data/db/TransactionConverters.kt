package com.moneydelta.app.data.db

import androidx.room.TypeConverter
import com.moneydelta.app.domain.model.TransactionType

class TransactionConverters {
    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.entries.firstOrNull { it.name == value } ?: TransactionType.EXPENSE
}
