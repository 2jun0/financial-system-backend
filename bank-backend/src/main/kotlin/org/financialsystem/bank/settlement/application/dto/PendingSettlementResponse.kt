package org.financialsystem.bank.settlement.application.dto

data class PendingSettlementResponse(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: Long,
)
