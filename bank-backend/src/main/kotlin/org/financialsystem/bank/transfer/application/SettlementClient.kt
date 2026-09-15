package org.financialsystem.bank.transfer.application

import org.financialsystem.bank.transfer.application.dto.InitiateSettlementRequest

interface SettlementClient {
    fun initiate(request: InitiateSettlementRequest)
}
