package org.financialsystem.settlement.settlement.application.dto

import jakarta.validation.constraints.NotBlank
import org.financialsystem.settlement.money.domain.PositiveAmount
import java.time.Instant

data class SettlementRequest(
    @NotBlank
    val transferKey: String,
    @NotBlank
    val sourceBankCode: String,
    @NotBlank
    val sourceAccountNumber: String,
    @NotBlank
    val recipientBankCode: String,
    @NotBlank
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
    val transferCreatedAt: Instant,
)
