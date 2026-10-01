package org.financialsystem.bank.transfer.application

import org.financialsystem.bank.account.application.exception.AccountNotFoundException
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementState
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.financialsystem.bank.transfer.presentation.dto.TransferResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations

@Service
class TransferService(
    private val transferKeyGenerator: TransferKeyGenerator,
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
    private val settlementClient: SettlementClient,
    private val transactionOperations: TransactionOperations,
    @Value($$"${bank.code}") private val bankCode: String,
) {
    private val logger = LoggerFactory.getLogger(TransferService::class.java)

    fun transfer(
        customerId: Long,
        sourceAccountNumber: String,
        transferRequest: TransferRequest,
    ): TransferResponse {
        if (transferRequest.recipientBankCode == bankCode) {
            throw SameBankTransferNotSupportException("같은 은행 간의 송금은 지원하지 않습니다.")
        }

        val transferKey = transferKeyGenerator.generate()
        transactionOperations.executeWithoutResult {
            initiateTransfer(transferKey, customerId, sourceAccountNumber, transferRequest)
        }

        try {
            val settlementResponse =
                settlementClient.requestSettlement(
                    SettlementRequest(
                        transferKey = transferKey,
                        sourceBankCode = bankCode,
                        sourceAccountNumber = sourceAccountNumber,
                        recipientBankCode = transferRequest.recipientBankCode,
                        recipientAccountNumber = transferRequest.recipientAccountNumber,
                        amount = transferRequest.amount,
                    ),
                )
            return transactionOperations.execute {
                applySettlementResult(transferKey, settlementResponse.state)
            }
        } catch (exception: SettlementClientException) {
            logger.warn("결제 요청 실패, transferKey={}", transferKey, exception)
            return TransferResponse(
                transferKey = transferKey,
                state = TransferState.TRANSFERRING,
            )
        }
    }

    private fun initiateTransfer(
        transferKey: String,
        customerId: Long,
        sourceAccountNumber: String,
        transferRequest: TransferRequest,
    ) {
        val account = accountRepository.findByAccountNumber(sourceAccountNumber)
        if (account == null || account.customerId != customerId) {
            throw AccountNotFoundException("계좌를 찾을 수 없습니다. : $sourceAccountNumber")
        }

        val sourceAccountId = account.id!!
        account.debit(transferRequest.amount)
        accountTransactionRepository.save(
            AccountTransaction.recordDebit(
                sourceAccountId,
                transferKey,
                transferRequest.recipientBankCode,
                transferRequest.recipientAccountNumber,
                transferRequest.amount,
            ),
        )

        transferRepository.save(
            Transfer.initiate(
                transferKey,
                sourceAccountId,
                transferRequest.recipientBankCode,
                transferRequest.recipientAccountNumber,
                transferRequest.amount,
            ),
        )
    }

    private fun applySettlementResult(
        transferKey: String,
        settlementState: SettlementState,
    ): TransferResponse {
        val transfer = transferRepository.findByTransferKey(transferKey)!!

        when (settlementState) {
            SettlementState.SETTLED -> transfer.succeed()
            SettlementState.REJECTED -> {
                compensateForFailure(transfer)
                transfer.fail()
            }
            SettlementState.RECIPIENT_VALIDATION_PENDING -> Unit
        }

        return TransferResponse.from(transfer)
    }

    private fun compensateForFailure(transfer: Transfer) {
        val account = accountRepository.findByIdOrNull(transfer.sourceAccountId)!!
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
