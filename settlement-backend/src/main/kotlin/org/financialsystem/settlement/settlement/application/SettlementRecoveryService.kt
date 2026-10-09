package org.financialsystem.settlement.settlement.application

import jakarta.transaction.Transactional
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

const val BATCH_GET_SETTLEMENT_STATES_MAX_SIZE = 100

@Service
class SettlementRecoveryService(
    private val clock: Clock,
    private val settlementRepository: SettlementRepository,
) {
    @Transactional
    fun recoverUnresolvedSettlements() {
        var cursorCreatedAt = Instant.EPOCH
        var cursorId = 0L
        val createdAtBefore = Instant.now(clock)
        val transferCreatedAtBefore = createdAtBefore.minus(SETTLEMENT_TIME_LIMIT)

        while (true) {
            val unresolvedSettlements =
                settlementRepository.findRecoveryTargets(
                    state = SettlementState.RECIPIENT_VALIDATION_PENDING,
                    transferCreatedAtBefore = transferCreatedAtBefore,
                    createdAtBefore = createdAtBefore,
                    cursorCreatedAt = cursorCreatedAt,
                    cursorId = cursorId,
                    limit = Limit.of(BATCH_GET_SETTLEMENT_STATES_MAX_SIZE),
                )
            if (unresolvedSettlements.isEmpty()) {
                break
            }
            val lastSettlement = unresolvedSettlements.last()
            cursorCreatedAt = lastSettlement.createdAt!!
            cursorId = lastSettlement.id!!
            unresolvedSettlements.forEach {
                it.reject()
            }
        }
    }
}
