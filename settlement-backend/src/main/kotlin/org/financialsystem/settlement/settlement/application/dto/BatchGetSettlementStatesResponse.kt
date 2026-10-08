package org.financialsystem.settlement.settlement.application.dto

import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState

data class BatchGetSettlementStatesResponse(
    val states: Map<String, SettlementState>,
) {
    companion object {
        fun from(settlements: List<Settlement>): BatchGetSettlementStatesResponse =
            BatchGetSettlementStatesResponse(
                states = settlements.associate { it.transferKey to it.state },
            )
    }
}
