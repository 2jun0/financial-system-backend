package org.financialsystem.bank.settlement.application.dto

data class BatchGetSettlementStatesRequest(
    val bankCode: String,
    val transferKeys: List<String>,
)
