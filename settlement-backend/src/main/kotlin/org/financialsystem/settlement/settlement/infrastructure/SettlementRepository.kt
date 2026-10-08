package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.domain.Settlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementRepository : JpaRepository<Settlement, Long> {
    fun findByTransferKey(transferKey: String): Settlement?

    @Query(
        """
        SELECT settlement
        FROM Settlement settlement
        WHERE settlement.transferKey IN :transferKeys
            AND (settlement.sourceBankId = :bankId OR settlement.recipientBankId = :bankId)
        """,
    )
    fun findAllByBankIdAndTransferKeyIn(
        @Param("bankId") bankId: Long,
        @Param("transferKeys") transferKeys: List<String>,
    ): List<Settlement>
}
