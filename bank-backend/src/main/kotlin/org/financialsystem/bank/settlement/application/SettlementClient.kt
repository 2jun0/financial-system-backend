package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.settlement.application.dto.InitiateSettlementRequest

interface SettlementClient {
    fun initiate(request: InitiateSettlementRequest)
}
