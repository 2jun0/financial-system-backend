package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse

interface SettlementClient {
    fun requestSettlement(request: SettlementRequest): SettlementResponse
}
