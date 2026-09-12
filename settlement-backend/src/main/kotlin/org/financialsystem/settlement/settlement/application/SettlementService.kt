package org.financialsystem.settlement.settlement.application

import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.SameBankSettlementException
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val PENDING_SETTLEMENT_COUNT = 100

@Service
class SettlementService(
    private val bankRepository: BankRepository,
    private val settlementRepository: SettlementRepository,
) {
    @Transactional
    fun initiate(request: InitiateSettlementRequest): InitiateSettlementResponse {
        if (request.sourceBankCode == request.recipientBankCode) {
            throw SameBankSettlementException("동일 은행 간 결제는 할 수 없습니다.")
        }

        val sourceBank =
            bankRepository.findByBankCode(request.sourceBankCode)
                ?: throw BankNotFoundException("송금 은행을 찾을 수 없습니다.")
        val recipientBank =
            bankRepository.findByBankCode(request.recipientBankCode)
                ?: throw BankNotFoundException("수취 은행을 찾을 수 없습니다.")

        val existingSettlement = settlementRepository.findByTransferKey(request.transferKey)
        if (existingSettlement != null) {
            if (!existingSettlement.matchesInitiation(
                    sourceBankId = sourceBank.id!!,
                    sourceAccountNumber = request.sourceAccountNumber,
                    recipientBankId = recipientBank.id!!,
                    recipientAccountNumber = request.recipientAccountNumber,
                    amount = request.amount,
                )
            ) {
                throw TransferKeyDuplicatedException()
            }

            return InitiateSettlementResponse.from(existingSettlement)
        }

        val settlement =
            Settlement.initiate(
                request.transferKey,
                sourceBank.id!!,
                request.sourceAccountNumber,
                recipientBank.id!!,
                request.recipientAccountNumber,
                request.amount,
            )

        settlementRepository.save(settlement)
        return InitiateSettlementResponse.from(settlement)
    }

    @Transactional(readOnly = true)
    fun findPendingSettlements(recipientBankCode: String): List<PendingSettlementResponse> {
        val recipientBank =
            bankRepository.findByBankCode(recipientBankCode)
                ?: throw BankNotFoundException("수취 은행을 찾을 수 없습니다.")

        val pendingSettlements =
            settlementRepository
                .findByRecipientBankIdAndState(
                    recipientBank.id!!,
                    SettlementState.RECIPIENT_VALIDATION_PENDING,
                    PageRequest.of(0, PENDING_SETTLEMENT_COUNT, Sort.by("createdAt", "id").ascending()),
                )

        val sourceBankIds = pendingSettlements.map { it.sourceBankId }.toSet()
        val sourceBankCodes = bankRepository.findAllById(sourceBankIds).associate { it.id!! to it.bankCode }

        return pendingSettlements.map {
            PendingSettlementResponse.from(it, sourceBankCodes.getValue(it.sourceBankId), recipientBankCode)
        }
    }
}
