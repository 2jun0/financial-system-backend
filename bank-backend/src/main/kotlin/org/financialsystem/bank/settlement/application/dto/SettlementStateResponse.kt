package org.financialsystem.bank.settlement.application.dto

enum class SettlementState {
    RECIPIENT_VALIDATION_PENDING,
    SETTLED,
    REJECTED,
}

data class SettlementStateResponse(
    val transferKey: String,
    val state: SettlementState,
)
