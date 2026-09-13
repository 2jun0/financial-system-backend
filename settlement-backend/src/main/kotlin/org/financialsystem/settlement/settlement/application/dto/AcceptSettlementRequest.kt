package org.financialsystem.settlement.settlement.application.dto

import jakarta.validation.constraints.NotBlank

data class AcceptSettlementRequest(
    @NotBlank
    val recipientBankCode: String,
)
