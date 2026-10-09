package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse
import java.time.Duration

val SETTLEMENT_TIME_LIMIT: Duration = Duration.ofSeconds(300)

interface SettlementClient {
    fun requestSettlement(request: SettlementRequest): SettlementResponse

    fun batchGetSettlementStates(request: BatchGetSettlementStatesRequest): BatchGetSettlementStatesResponse
}
