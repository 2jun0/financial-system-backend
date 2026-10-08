package org.financialsystem.bank.settlement.application.dto

data class BatchGetSettlementStatesResponse(
    val states: Map<String, SettlementState>,
)
