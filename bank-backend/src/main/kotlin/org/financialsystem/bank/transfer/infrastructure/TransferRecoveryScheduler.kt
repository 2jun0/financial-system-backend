package org.financialsystem.bank.transfer.infrastructure

import org.financialsystem.bank.transfer.application.TransferRecoveryService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TransferRecoveryScheduler(
    private val transferRecoveryService: TransferRecoveryService,
) {
    @Scheduled(fixedDelayString = $$"${transfer.recovery.fixed-delay:30000}")
    fun recoverUnresolvedTransfers() {
        transferRecoveryService.recoverUnresolvedTransfers()
    }
}
