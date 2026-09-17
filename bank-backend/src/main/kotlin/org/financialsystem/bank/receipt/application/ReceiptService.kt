package org.financialsystem.bank.receipt.application

import jakarta.transaction.Transactional
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
) {
    @Transactional
    fun completeReceipt(receiptId: Long) {
        val receipt = receiptRepository.findByIdOrNull(receiptId)!!
        receipt.succeed()

        val account = accountRepository.findByIdOrNull(receipt.recipientAccountId)!!
        account.credit(PositiveAmount(receipt.amount))

        accountTransactionRepository.save(
            AccountTransaction.recordCredit(
                receipt.recipientAccountId,
                receipt.transferKey,
                receipt.sourceBankCode,
                receipt.sourceAccountNumber,
                PositiveAmount(receipt.amount),
            ),
        )
    }
}
