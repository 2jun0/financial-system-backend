package org.financialsystem.bank.settlement.application.dto

data class SettlementResponse(
    val transferKey: String,
    val state: SettlementState,
)
