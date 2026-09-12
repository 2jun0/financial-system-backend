package org.financialsystem.settlement.settlement.application.dto

import org.financialsystem.settlement.settlement.domain.Settlement

data class PendingSettlementResponse(
    val transferKey: String,
    val sourceBankCode: String,
    val sourceAccountNumber: String,
    val recipientBankCode: String,
    val recipientAccountNumber: String,
    val amount: Long,
) {
    companion object {
        fun from(
            settlement: Settlement,
            sourceBankCode: String,
            recipientBankCode: String,
        ): PendingSettlementResponse =
            PendingSettlementResponse(
                transferKey = settlement.transferKey,
                sourceBankCode = sourceBankCode,
                sourceAccountNumber = settlement.sourceAccountNumber,
                recipientBankCode = recipientBankCode,
                recipientAccountNumber = settlement.recipientAccountNumber,
                amount = settlement.amount,
            )
    }
}
