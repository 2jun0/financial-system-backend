package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.application.SettlementRecoveryService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SettlementRecoveryScheduler(
    private val settlementRecoveryService: SettlementRecoveryService,
) {
    @Scheduled(fixedDelay = 30_000L)
    fun recoverUnresolvedSettlements() {
        settlementRecoveryService.recoverUnresolvedSettlements()
    }
}
