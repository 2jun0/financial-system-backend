package org.financialsystem.bank.settlement.application.dto

import org.financialsystem.bank.money.domain.PositiveAmount
import java.time.Instant

data class SettlementRequest(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: PositiveAmount,
    val transferCreatedAt: Instant,
)
