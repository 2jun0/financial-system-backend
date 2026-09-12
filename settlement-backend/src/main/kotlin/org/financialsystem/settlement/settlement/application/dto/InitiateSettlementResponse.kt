package org.financialsystem.settlement.settlement.application.dto

import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState

data class InitiateSettlementResponse(
    val transferKey: String,
    val state: SettlementState,
) {
    companion object {
        fun from(settlement: Settlement): InitiateSettlementResponse =
            InitiateSettlementResponse(
                transferKey = settlement.transferKey,
                state = settlement.state,
            )
    }
}
