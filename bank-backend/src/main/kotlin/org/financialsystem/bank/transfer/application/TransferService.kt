package org.financialsystem.bank.transfer.application

import jakarta.transaction.Transactional
import org.financialsystem.bank.account.application.exception.AccountNotFoundException
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.infrastructure.TransferRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val transferKeyGenerator: TransferKeyGenerator,
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
    @Value($$"${bank.code}") private val bankCode: String,
) {
    @Transactional
    fun initiateTransfer(
        customerId: Long,
        sourceAccountId: Long,
        transferRequest: TransferRequest,
    ): Transfer {
        if (transferRequest.recipientBankCode == bankCode) {
            throw SameBankTransferNotSupportException("같은 은행 간의 송금은 지원하지 않습니다.")
        }

        return initiateTransferToOtherBank(
            sourceAccountId,
            customerId,
            transferRequest.amount,
            transferRequest.recipientBankCode,
            transferRequest.recipientAccountNumber,
        )
    }

    private fun initiateTransferToOtherBank(
        sourceAccountId: Long,
        customerId: Long,
        amount: PositiveAmount,
        recipientBankCode: String,
        recipientAccountNumber: String,
    ): Transfer {
        val account = accountRepository.findByIdOrNull(sourceAccountId)
        if (account == null || account.customerId != customerId) {
            throw AccountNotFoundException("계좌를 찾을 수 없습니다. : $sourceAccountId")
        }

        val transferKey = transferKeyGenerator.generate()
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
