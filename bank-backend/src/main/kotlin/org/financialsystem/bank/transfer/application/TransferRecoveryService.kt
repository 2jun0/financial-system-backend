package org.financialsystem.bank.transfer.application

import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SETTLEMENT_TIME_LIMIT
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.dto.SettlementState.REJECTED
import org.financialsystem.bank.settlement.application.dto.SettlementState.SETTLED
import org.financialsystem.bank.settlement.infrastructure.BATCH_GET_SETTLEMENT_STATES_MAX_SIZE
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant

@Service
class TransferRecoveryService(
    private val clock: Clock,
    @Value($$"${bank.code}") private val bankCode: String,
    private val transferRepository: TransferRepository,
    private val settlementClient: SettlementClient,
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
    private val transactionOperations: TransactionOperations,
) {
    fun recoverUnresolvedTransfers() {
        var cursorCreatedAt = Instant.EPOCH
        var cursorId = 0L
        val createdAtBefore = Instant.now(clock).minus(SETTLEMENT_TIME_LIMIT)

        while (true) {
            val unresolvedTransfers =
                transferRepository
                    .findRecoveryTargets(
                        state = TransferState.TRANSFERRING,
                        createdAtBefore = createdAtBefore,
                        cursorCreatedAt = cursorCreatedAt,
                        cursorId = cursorId,
                        limit = Limit.of(BATCH_GET_SETTLEMENT_STATES_MAX_SIZE),
                    )
            if (unresolvedTransfers.isEmpty()) {
                break
            }
            val lastTransfer = unresolvedTransfers.last()
            cursorCreatedAt = lastTransfer.createdAt!!
            cursorId = lastTransfer.id!!

            val unresolvedTransferKeys = unresolvedTransfers.map { it.transferKey }
            val response =
                settlementClient.batchGetSettlementStates(
                    BatchGetSettlementStatesRequest(
                        bankCode,
                        unresolvedTransferKeys,
                    ),
                )
            transactionOperations.executeWithoutResult {
                batchRecoverUnresolvedTransfers(unresolvedTransferKeys, response.states)
            }
        }
    }

    private fun batchRecoverUnresolvedTransfers(
        transferKeys: List<String>,
        settlementStates: Map<String, SettlementState>,
    ) {
        val transfers = transferRepository.findAllByTransferKeyIn(transferKeys)
        val accounts = accountRepository.findAllById(transfers.map { it.sourceAccountId }).associateBy { it.id }

        for (transfer in transfers) {
            val state = settlementStates[transfer.transferKey]
            if (state == SETTLED) {
                transfer.succeed()
            } else if (state == REJECTED || state == null) {
                val account = accounts.getValue(transfer.sourceAccountId)
                compensateForFailure(transfer, account)
                transfer.fail()
            }
        }
    }

    private fun compensateForFailure(
        transfer: Transfer,
        account: Account,
    ) {
        val amount = PositiveAmount(transfer.amount)
        val accountTransaction =
            AccountTransaction.recordCompensation(
                accountId = account.id!!,
                transferKey = transfer.transferKey,
                recipientBankCode = transfer.recipientBankCode,
                recipientAccountNumber = transfer.recipientAccountNumber,
                amount = amount,
            )

        accountTransactionRepository.save(accountTransaction)
        account.credit(amount)
    }
}
