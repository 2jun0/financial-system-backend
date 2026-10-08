package org.financialsystem.settlement.settlement.application.dto

import org.financialsystem.settlement.money.domain.PositiveAmount
import java.time.Instant

data class ValidateSettlementRequest(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
    val transferCreatedAt: Instant,
)
