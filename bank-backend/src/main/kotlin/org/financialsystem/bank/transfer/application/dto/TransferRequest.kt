package org.financialsystem.bank.transfer.application.dto

import jakarta.validation.constraints.NotBlank
import org.financialsystem.bank.money.domain.PositiveAmount

data class TransferRequest(
    @NotBlank
    val recipientBankCode: String,
    @NotBlank
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
)
