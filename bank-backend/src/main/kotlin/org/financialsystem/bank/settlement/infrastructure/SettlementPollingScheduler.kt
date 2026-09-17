package org.financialsystem.bank.settlement.infrastructure

import org.financialsystem.bank.settlement.application.RecipientSettlementService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SettlementPollingScheduler(
    private val recipientSettlementService: RecipientSettlementService,
) {
    @Scheduled(
        fixedDelayString = "\${settlement.polling.delay}",
    )
    fun poll() {
        recipientSettlementService.decidePendingSettlements()
        recipientSettlementService.updateReceivingReceipts()
    }
}
