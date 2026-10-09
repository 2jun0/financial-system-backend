package org.financialsystem.bank.transfer.infrastructure

import org.financialsystem.bank.transfer.application.TransferRecoveryService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TransferRecoveryScheduler(
    private val transferRecoveryService: TransferRecoveryService,
) {
    @Scheduled(fixedDelay = 30_000L)
    fun recoverUnresolvedTransfers() {
        transferRecoveryService.recoverUnresolvedTransfers()
    }
}
