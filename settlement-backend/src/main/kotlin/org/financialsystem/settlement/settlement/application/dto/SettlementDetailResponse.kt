package org.financialsystem.settlement.settlement.application.dto

import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState

data class SettlementDetailResponse(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: Long,
    val state: SettlementState,
) {
    companion object {
        fun from(
            settlement: Settlement,
            sourceBankCode: String,
            recipientBankCode: String,
        ): SettlementDetailResponse =
            SettlementDetailResponse(
                transferKey = settlement.transferKey,
                sourceBankCode = sourceBankCode,
                sourceAccountNumber = settlement.sourceAccountNumber,
                recipientBankCode = recipientBankCode,
                recipientAccountNumber = settlement.recipientAccountNumber,
                amount = settlement.amount,
                state = settlement.state,
            )
    }
}
