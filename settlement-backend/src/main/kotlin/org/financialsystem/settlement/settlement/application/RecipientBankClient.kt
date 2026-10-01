package org.financialsystem.settlement.settlement.application

import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementResponse

interface RecipientBankClient {
    fun validateSettlement(
        bankUri: String,
        request: ValidateSettlementRequest,
    ): ValidateSettlementResponse

    fun notifySettledResult(
        bankUri: String,
        transferKey: String,
    )

    fun notifyRejectedResult(
        bankUri: String,
        transferKey: String,
    )
}
