package com.moneydelta.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moneydelta.app.domain.model.BankRule

@Entity(tableName = "bank_rules")
data class BankRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val senderPattern: String,
    val incomeKeywords: String,
    val expenseKeywords: String,
    val ignoreKeywords: String,
    val amountRegex: String = "",
    val isEnabled: Boolean = true
) {
    fun toDomain(): BankRule = BankRule(
        id = id,
        name = name,
        senderPattern = senderPattern,
        incomeKeywords = incomeKeywords,
        expenseKeywords = expenseKeywords,
        ignoreKeywords = ignoreKeywords,
        amountRegex = amountRegex,
        isEnabled = isEnabled
    )

    companion object {
        fun fromDomain(rule: BankRule): BankRuleEntity = BankRuleEntity(
            id = rule.id,
            name = rule.name,
            senderPattern = rule.senderPattern,
            incomeKeywords = rule.incomeKeywords,
            expenseKeywords = rule.expenseKeywords,
            ignoreKeywords = rule.ignoreKeywords,
            amountRegex = rule.amountRegex,
            isEnabled = rule.isEnabled
        )
    }
}
