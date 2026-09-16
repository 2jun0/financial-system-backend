package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.bank.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RecipientSettlementService(
    private val settlementClient: SettlementClient,
    private val accountRepository: AccountRepository,
    @Value($$"${bank.code}") private val bankCode: String,
) {
    private val logger = LoggerFactory.getLogger(RecipientSettlementService::class.java)

    fun decidePendingSettlements() {
        val pendingSettlementResponses =
            try {
                settlementClient.findPendingSettlements(bankCode)
            } catch (exception: SettlementClientException) {
                logger.warn("중앙 결제 조회 실패", exception)
                return
            }

        pendingSettlementResponses.forEach { settlement ->
            val account = accountRepository.findByAccountNumber(settlement.recipientAccountNumber)

            try {
                if (account == null) {
                    settlementClient.reject(settlement.transferKey, RejectSettlementRequest(bankCode))
                } else {
                    settlementClient.accept(settlement.transferKey, AcceptSettlementRequest(bankCode))
                }
            } catch (exception: SettlementClientException) {
                logger.warn("중앙 결제 승인, 거절 전달 실패, transferKey={}", settlement.transferKey, exception)
            }
        }
    }
}
