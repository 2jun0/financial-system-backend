package org.financialsystem.bank.settlement.application.dto

import org.financialsystem.bank.money.domain.PositiveAmount

data class InitiateSettlementRequest(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
)
