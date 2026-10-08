package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

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

    @Query(
        """
        SELECT settlement
        FROM Settlement settlement
        WHERE settlement.state = :state
            AND settlement.transferCreatedAt <= :transferCreatedAtBefore
            AND settlement.createdAt <= :createdAtBefore
            AND (settlement.createdAt > :cursorCreatedAt
                OR (settlement.createdAt = :cursorCreatedAt AND settlement.id > :cursorId))
        ORDER BY settlement.createdAt ASC, settlement.id ASC
        """,
    )
    fun findRecoveryTargets(
        @Param("state") state: SettlementState,
        @Param("transferCreatedAtBefore") transferCreatedAtBefore: Instant,
        @Param("createdAtBefore") createdAtBefore: Instant,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        limit: Limit,
    ): List<Settlement>
}
