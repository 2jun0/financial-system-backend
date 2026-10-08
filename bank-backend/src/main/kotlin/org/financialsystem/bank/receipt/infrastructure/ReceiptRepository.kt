package org.financialsystem.bank.receipt.infrastructure

import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ReceiptRepository : JpaRepository<Receipt, Long> {
    fun findByTransferKey(transferKey: String): Receipt?

    fun existsByTransferKey(transferKey: String): Boolean

    @Query(
        """
        SELECT receipt
        FROM Receipt receipt
        WHERE receipt.state = :state
            AND receipt.transferCreatedAt <= :transferCreatedAtBefore
            AND receipt.createdAt <= :createdAtBefore
            AND (receipt.createdAt > :cursorCreatedAt
                OR (receipt.createdAt = :cursorCreatedAt AND receipt.id > :cursorId))
        ORDER BY receipt.createdAt ASC, receipt.id ASC
        """,
    )
    fun findRecoveryTargets(
        @Param("state") state: ReceiptState,
        @Param("transferCreatedAtBefore") transferCreatedAtBefore: Instant,
        @Param("createdAtBefore") createdAtBefore: Instant,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        limit: Limit,
    ): List<Receipt>

    fun findAllByTransferKeyIn(transferKeys: List<String>): List<Receipt>
}
