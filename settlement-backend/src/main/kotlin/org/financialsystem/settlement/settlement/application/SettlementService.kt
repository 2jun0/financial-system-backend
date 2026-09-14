package org.financialsystem.settlement.settlement.application

import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementStateResponse
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.RecipientBankMismatchException
import org.financialsystem.settlement.settlement.exception.SameBankSettlementException
import org.financialsystem.settlement.settlement.exception.SettlementNotFoundException
import org.financialsystem.settlement.settlement.exception.SourceBankMismatchException
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
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

    @Transactional
    fun accept(
        transferKey: String,
        request: AcceptSettlementRequest,
    ): SettlementStateResponse {
        val settlement = findSettlement(transferKey)
        val recipientBank = bankRepository.findByIdOrNull(settlement.recipientBankId)!!

        validateRecipientBank(request.recipientBankCode, recipientBank.bankCode)

        if (settlement.state == SettlementState.SETTLED || settlement.state == SettlementState.REJECTED) {
            return SettlementStateResponse.from(settlement)
        }

        val sourceBank = bankRepository.findByIdOrNull(settlement.sourceBankId)!!
        if (sourceBank.balance < settlement.amount) {
            settlement.reject()
        } else {
            sourceBank.debit(PositiveAmount(settlement.amount))
            recipientBank.credit(PositiveAmount(settlement.amount))
            settlement.settle()
        }

        return SettlementStateResponse.from(settlement)
    }

    @Transactional
    fun reject(
        transferKey: String,
        request: RejectSettlementRequest,
    ): SettlementStateResponse {
        val settlement = findSettlement(transferKey)
        val recipientBank = bankRepository.findByIdOrNull(settlement.recipientBankId)!!

        validateRecipientBank(request.recipientBankCode, recipientBank.bankCode)

        if (settlement.state == SettlementState.SETTLED || settlement.state == SettlementState.REJECTED) {
            return SettlementStateResponse.from(settlement)
        }

        settlement.reject()
        return SettlementStateResponse.from(settlement)
    }

    @Transactional(readOnly = true)
    fun find(
        transferKey: String,
        sourceBankCode: String,
    ): SettlementDetailResponse {
        val settlement = findSettlement(transferKey)
        val sourceBank = bankRepository.findByIdOrNull(settlement.sourceBankId)!!

        validateSourceBank(sourceBankCode, sourceBank.bankCode)

        val recipientBank = bankRepository.findByIdOrNull(settlement.recipientBankId)!!
        return SettlementDetailResponse.from(settlement, sourceBank.bankCode, recipientBank.bankCode)
    }

    private fun findSettlement(transferKey: String): Settlement =
        settlementRepository.findByTransferKey(transferKey)
            ?: throw SettlementNotFoundException("존재하지 않는 결제 정보입니다.")

    private fun validateRecipientBank(
        requestedBankCode: String,
        recipientBankCode: String,
    ) {
        if (requestedBankCode != recipientBankCode) {
            throw RecipientBankMismatchException("지정된 수취 은행만 결제를 승인하거나 거절할 수 있습니다.")
        }
    }

    private fun validateSourceBank(
        requestedBankCode: String,
        sourceBankCode: String,
    ) {
        if (requestedBankCode != sourceBankCode) {
            throw SourceBankMismatchException("지정된 송금 은행만 결제를 조회할 수 있습니다.")
        }
    }
}
