package org.financialsystem.bank.transfer.application

import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TransferService(
    private val transferKeyGenerator: TransferKeyGenerator,
    private val transferInitiationService: TransferInitiationService,
    @Value($$"${bank.code}") private val bankCode: String,
) {
    fun initiateTransfer(
        customerId: Long,
        sourceAccountNumber: String,
        transferRequest: TransferRequest,
    ): Transfer {
        if (transferRequest.recipientBankCode == bankCode) {
            throw SameBankTransferNotSupportException("같은 은행 간의 송금은 지원하지 않습니다.")
        }

        val transferKey = transferKeyGenerator.generate()
        val transfer =
            transferInitiationService.initiateTransfer(
                transferKey,
                customerId,
                sourceAccountNumber,
                transferRequest,
            )

        return transfer
    }
}
