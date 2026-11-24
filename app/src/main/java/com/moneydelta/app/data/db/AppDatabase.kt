package com.moneydelta.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moneydelta.app.domain.parser.DefaultBankRules

@Database(
    entities = [TransactionEntity::class, BankRuleEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TransactionConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun bankRuleDao(): BankRuleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "money_delta.db"
            )
                .addCallback(
                    object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            DefaultBankRules.starterRules().forEach { rule ->
                                val values = ContentValues().apply {
                                    put("name", rule.name)
                                    put("senderPattern", rule.senderPattern)
                                    put("incomeKeywords", rule.incomeKeywords)
                                    put("expenseKeywords", rule.expenseKeywords)
                                    put("ignoreKeywords", rule.ignoreKeywords)
                                    put("amountRegex", rule.amountRegex)
                                    put("isEnabled", if (rule.isEnabled) 1 else 0)
                                }
                                db.insert("bank_rules", SQLiteDatabase.CONFLICT_IGNORE, values)
                            }
                        }
                    }
                )
                .build()
        }
    }
}
