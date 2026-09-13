package org.financialsystem.settlement.settlement.application.dto

import jakarta.validation.constraints.NotBlank

data class RejectSettlementRequest(
    @NotBlank
    val recipientBankCode: String,
)
