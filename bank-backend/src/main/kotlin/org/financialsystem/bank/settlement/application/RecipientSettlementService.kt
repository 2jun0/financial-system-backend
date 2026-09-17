package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.application.ReceiptService
import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.bank.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.bank.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class RecipientSettlementService(
    private val settlementClient: SettlementClient,
    private val accountRepository: AccountRepository,
    private val receiptRepository: ReceiptRepository,
    private val receiptService: ReceiptService,
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
            decideSettlement(settlement)
        }
    }

    private fun decideSettlement(settlement: PendingSettlementResponse) {
        val account = accountRepository.findByAccountNumber(settlement.recipientAccountNumber)

        try {
            if (account == null) {
                settlementClient.reject(settlement.transferKey, RejectSettlementRequest(bankCode))
            } else {
                receiptRepository.save(
                    Receipt.initiate(
                        settlement.transferKey,
                        account.id!!,
                        settlement.sourceBankCode,
                        settlement.sourceAccountNumber,
                        PositiveAmount(settlement.amount),
                    ),
                )
                settlementClient.accept(settlement.transferKey, AcceptSettlementRequest(bankCode))
            }
        } catch (exception: SettlementClientException) {
            logger.warn("중앙 결제 승인, 거절 전달 실패, transferKey={}", settlement.transferKey, exception)
        }
    }

    fun updateReceivingReceipts() {
        val receipts = receiptRepository.findAllByState(ReceiptState.RECEIVING)
        receipts.forEach { receipt ->
            val settlement = settlementClient.find(receipt.transferKey)
            if (settlement.state == SettlementState.SETTLED) {
                receiptService.completeReceipt(receipt.id!!)
            } else if (settlement.state == SettlementState.REJECTED) {
                receipt.fail()
            }
        }
    }
}
