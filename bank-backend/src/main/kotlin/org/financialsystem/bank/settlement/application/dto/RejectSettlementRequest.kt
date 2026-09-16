package org.financialsystem.bank.settlement.application.dto

data class RejectSettlementRequest(
    val recipientBankCode: String,
)
