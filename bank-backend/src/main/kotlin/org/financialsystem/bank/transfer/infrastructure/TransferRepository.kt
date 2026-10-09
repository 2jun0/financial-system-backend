package org.financialsystem.bank.transfer.infrastructure

import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TransferRepository : JpaRepository<Transfer, Long> {
    fun findByTransferKey(transferKey: String): Transfer?

    fun findAllByTransferKeyIn(transferKeys: Collection<String>): List<Transfer>

    @Query(
        """
        SELECT transfer
        FROM Transfer transfer
        WHERE transfer.state = :state
            AND transfer.createdAt <= :createdAtBefore
            AND (transfer.createdAt > :cursorCreatedAt
                OR (transfer.createdAt = :cursorCreatedAt AND transfer.id > :cursorId))
        ORDER BY transfer.createdAt ASC, transfer.id ASC
        """,
    )
    fun findRecoveryTargets(
        @Param("state") state: TransferState,
        @Param("createdAtBefore") createdAtBefore: Instant,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant,
        @Param("cursorId") cursorId: Long,
        limit: Limit,
    ): List<Transfer>
}
