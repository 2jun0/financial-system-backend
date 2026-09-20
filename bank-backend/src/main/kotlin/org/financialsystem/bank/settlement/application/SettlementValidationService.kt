package org.financialsystem.bank.settlement.application

import jakarta.transaction.Transactional
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementResponse
import org.springframework.stereotype.Service

@Service
class SettlementValidationService(
    private val accountRepository: AccountRepository,
    private val receiptRepository: ReceiptRepository,
) {
    @Transactional
    fun validateSettlement(request: ValidateSettlementRequest): ValidateSettlementResponse {
        val account =
            accountRepository.findByAccountNumber(request.recipientAccountNumber)
                ?: return ValidateSettlementResponse(isAccepted = false)

        if (!receiptRepository.existsByTransferKey(request.transferKey)) {
            receiptRepository.save(
                Receipt.initiate(
                    transferKey = request.transferKey,
                    recipientAccountId = account.id!!,
                    sourceBankCode = request.sourceBankCode,
                    sourceAccountNumber = request.sourceAccountNumber,
                    amount = request.amount,
                ),
            )
        }

        return ValidateSettlementResponse(isAccepted = true)
    }
}
