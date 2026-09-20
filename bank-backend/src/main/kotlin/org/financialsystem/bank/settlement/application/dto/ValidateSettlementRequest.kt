package org.financialsystem.bank.settlement.application.dto

import jakarta.validation.constraints.NotBlank
import org.financialsystem.bank.money.domain.PositiveAmount

data class ValidateSettlementRequest(
    @NotBlank
    val transferKey: String,
    @NotBlank
    val sourceBankCode: String,
    @NotBlank
    val sourceAccountNumber: String,
    @NotBlank
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
)
