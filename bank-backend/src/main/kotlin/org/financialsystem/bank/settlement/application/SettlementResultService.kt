package org.financialsystem.bank.settlement.application

import jakarta.transaction.Transactional
import org.financialsystem.bank.account.domain.AccountTransaction
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.account.infrastructure.AccountTransactionRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class SettlementResultService(
    private val receiptRepository: ReceiptRepository,
    private val accountRepository: AccountRepository,
    private val accountTransactionRepository: AccountTransactionRepository,
) {
    @Transactional
    fun applySettledResult(transferKey: String) {
        val receipt =
            receiptRepository.findByTransferKey(transferKey)
                ?: error("수취 기록을 찾을 수 없습니다. transferKey=$transferKey")

        val account =
            accountRepository.findByIdOrNull(receipt.recipientAccountId)
                ?: error("수취 계좌를 찾을 수 없습니다. accountId=${receipt.recipientAccountId}")
        account.credit(PositiveAmount(receipt.amount))

        if (receipt.state == ReceiptState.SUCCEEDED) {
            return
        }
        check(receipt.state == ReceiptState.RECEIVING) {
            "실패한 수취를 성공 처리할 수 없습니다. transferKey=$transferKey"
        }

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
    }

    @Transactional
    fun applyRejectedResult(transferKey: String) {
        val receipt = receiptRepository.findByTransferKey(transferKey) ?: return

        if (receipt.state == ReceiptState.FAILED) {
            return
        }
        check(receipt.state == ReceiptState.RECEIVING) {
            "성공한 수취를 실패 처리할 수 없습니다. transferKey=$transferKey"
        }
        receipt.fail()
    }
}
