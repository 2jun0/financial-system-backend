package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun findByRecipientBankIdAndState(
        recipientBankId: Long,
        state: SettlementState,
        pageable: Pageable,
    ): MutableList<Settlement>
}
