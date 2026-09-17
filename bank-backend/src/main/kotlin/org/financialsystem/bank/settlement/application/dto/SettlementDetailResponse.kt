package org.financialsystem.bank.settlement.application.dto

data class SettlementDetailResponse(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: Long,
    val state: SettlementState,
)
