package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.bank.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.bank.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.bank.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementStateResponse

interface SettlementClient {
    fun initiate(request: InitiateSettlementRequest)

    fun findPendingSettlements(recipientBankCode: String): List<PendingSettlementResponse>

    fun reject(
        transferKey: String,
        request: RejectSettlementRequest,
    ): SettlementStateResponse

    fun accept(
        transferKey: String,
        request: AcceptSettlementRequest,
    ): SettlementStateResponse
}
