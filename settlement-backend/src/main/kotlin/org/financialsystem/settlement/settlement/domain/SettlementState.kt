package org.financialsystem.settlement.settlement.domain

enum class SettlementState {
    RECIPIENT_VALIDATION_PENDING,
    SETTLED,
    REJECTED,
}
