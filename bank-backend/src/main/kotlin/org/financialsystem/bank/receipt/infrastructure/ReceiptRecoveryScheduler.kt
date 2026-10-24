package org.financialsystem.bank.receipt.infrastructure

import org.financialsystem.bank.receipt.application.ReceiptRecoveryService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReceiptRecoveryScheduler(
    private val receiptRecoveryService: ReceiptRecoveryService,
) {
    @Scheduled(fixedDelayString = $$"${receipt.recovery.fixed-delay:30000}")
    fun recoverUnresolvedTransfers() {
        receiptRecoveryService.recoverUnresolvedReceipts()
    }
}
