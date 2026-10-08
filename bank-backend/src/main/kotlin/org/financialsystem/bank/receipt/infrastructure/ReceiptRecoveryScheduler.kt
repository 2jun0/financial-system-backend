package org.financialsystem.bank.receipt.infrastructure

import org.financialsystem.bank.receipt.application.ReceiptRecoveryService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReceiptRecoveryScheduler(
    private val receiptRecoveryService: ReceiptRecoveryService,
) {
    @Scheduled(fixedDelay = 30_000L)
    fun recoverUnresolvedTransfers() {
        receiptRecoveryService.recoverUnresolvedReceipts()
    }
}
