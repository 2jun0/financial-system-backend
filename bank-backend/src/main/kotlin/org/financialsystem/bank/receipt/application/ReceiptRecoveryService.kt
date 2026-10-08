package org.financialsystem.bank.receipt.application

import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.SETTLEMENT_TIME_LIMIT
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.dto.SettlementState.REJECTED
import org.financialsystem.bank.settlement.application.dto.SettlementState.SETTLED
import org.financialsystem.bank.settlement.infrastructure.BATCH_GET_SETTLEMENT_STATES_MAX_SIZE
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant

@Service
class ReceiptRecoveryService(
    private val clock: Clock,
    @Value($$"${bank.code}") private val bankCode: String,
    private val receiptRepository: ReceiptRepository,
    private val settlementClient: SettlementClient,
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
    private val transactionOperations: TransactionOperations,
) {
    fun recoverUnresolvedReceipts() {
        var cursorCreatedAt = Instant.EPOCH
        var cursorId = 0L
        val createdAtBefore = Instant.now(clock)
        val transferCreatedAtBefore = createdAtBefore.minus(SETTLEMENT_TIME_LIMIT)

        while (true) {
            val unresolvedReceipts =
                receiptRepository
                    .findRecoveryTargets(
                        state = ReceiptState.RECEIVING,
                        transferCreatedAtBefore = transferCreatedAtBefore,
                        createdAtBefore = createdAtBefore,
                        cursorCreatedAt = cursorCreatedAt,
                        cursorId = cursorId,
                        limit = Limit.of(BATCH_GET_SETTLEMENT_STATES_MAX_SIZE),
                    )
            if (unresolvedReceipts.isEmpty()) {
                break
            }
            val lastReceipt = unresolvedReceipts.last()
            cursorCreatedAt = lastReceipt.createdAt!!
            cursorId = lastReceipt.id!!

            val unresolvedTransferKeys = unresolvedReceipts.map { it.transferKey }
            val response =
                settlementClient.batchGetSettlementStates(
                    BatchGetSettlementStatesRequest(
                        bankCode,
                        unresolvedTransferKeys,
                    ),
                )
            transactionOperations.executeWithoutResult {
                batchRecoverUnresolvedReceipts(unresolvedTransferKeys, response.states)
            }
        }
    }

    private fun batchRecoverUnresolvedReceipts(
        transferKeys: List<String>,
        settlementStates: Map<String, SettlementState>,
    ) {
        val receipts = receiptRepository.findAllByTransferKeyIn(transferKeys)
        val accounts = accountRepository.findAllById(receipts.map { it.recipientAccountId }).associateBy { it.id }

        for (receipt in receipts) {
            val state = settlementStates[receipt.transferKey]
            if (state == SETTLED) {
                accounts.getValue(receipt.recipientAccountId).credit(PositiveAmount(receipt.amount))

                accountTransactionRepository.save(
                    AccountTransaction.recordCredit(
                        receipt.recipientAccountId,
                        receipt.transferKey,
                        receipt.sourceBankCode,
                        receipt.sourceAccountNumber,
                        PositiveAmount(receipt.amount),
                    ),
                )
                receipt.succeed()
            } else if (state == REJECTED || state == null) {
                receipt.fail()
            }
        }
    }
}
