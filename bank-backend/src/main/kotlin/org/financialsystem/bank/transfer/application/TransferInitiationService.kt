package org.financialsystem.bank.transfer.application

import jakarta.transaction.Transactional
import org.financialsystem.bank.account.application.exception.AccountNotFoundException
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.springframework.stereotype.Service

@Service
class TransferInitiationService(
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
) {
    @Transactional
    fun initiateTransfer(
        transferKey: String,
        customerId: Long,
        sourceAccountNumber: String,
        transferRequest: TransferRequest,
    ): Transfer =
        initiateTransferToOtherBank(
            transferKey,
            sourceAccountNumber,
            customerId,
            transferRequest.amount,
            transferRequest.recipientBankCode,
            transferRequest.recipientAccountNumber,
        )

    private fun initiateTransferToOtherBank(
        transferKey: String,
        sourceAccountNumber: String,
        customerId: Long,
        amount: PositiveAmount,
        recipientBankCode: String,
        recipientAccountNumber: String,
    ): Transfer {
        val account = accountRepository.findByAccountNumber(sourceAccountNumber)
        if (account == null || account.customerId != customerId) {
            throw AccountNotFoundException("계좌를 찾을 수 없습니다. : $sourceAccountNumber")
        }

        val sourceAccountId = account.id!!
        account.debit(amount)
        accountTransactionRepository.save(
            AccountTransaction.recordDebit(
                sourceAccountId,
                transferKey,
                recipientBankCode,
                recipientAccountNumber,
                amount,
            ),
        )

        return transferRepository.save(
            Transfer.initiate(
                transferKey,
                sourceAccountId,
                recipientBankCode,
                recipientAccountNumber,
                amount,
            ),
        )
    }
}
