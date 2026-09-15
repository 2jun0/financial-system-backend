package org.financialsystem.bank.transfer.application

import org.financialsystem.bank.transfer.application.dto.InitiateSettlementRequest
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.application.exception.SettlementClientException
import org.financialsystem.bank.transfer.domain.Transfer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class TransferService(
    private val transferKeyGenerator: TransferKeyGenerator,
    private val transferInitiationService: TransferInitiationService,
    private val settlementClient: SettlementClient,
    @Value($$"${bank.code}") private val bankCode: String,
) {
    private val logger = LoggerFactory.getLogger(TransferService::class.java)

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

        val settlementRequest =
            InitiateSettlementRequest(
                transferKey = transfer.transferKey,
                sourceBankCode = bankCode,
                sourceAccountNumber = sourceAccountNumber,
                recipientBankCode = transfer.recipientBankCode,
                recipientAccountNumber = transfer.recipientAccountNumber,
                amount = transferRequest.amount,
            )
        try {
            settlementClient.initiate(settlementRequest)
        } catch (exception: SettlementClientException) {
            logger.warn("중앙 결제 요청 실패, 송금중 유지, transferKey={}", transfer.transferKey, exception)
        }
        return transfer
    }
}
