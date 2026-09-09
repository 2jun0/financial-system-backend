package org.financialsystem.settlement.settlement.application

import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.exception.SameBankSettlementException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.springframework.stereotype.Service

@Service
class SettlementService(
    private val bankRepository: BankRepository,
    private val settlementRepository: SettlementRepository,
) {
    fun initiate(request: InitiateSettlementRequest) {
        if (request.sourceBankCode == request.recipientBankCode) {
            throw SameBankSettlementException("동일 은행 간 결제는 할 수 없습니다.")
        }

        val sourceBank =
            bankRepository.findByBankCode(request.sourceBankCode)
                ?: throw BankNotFoundException("송금 은행을 찾을 수 없습니다.")
        val recipientBank =
            bankRepository.findByBankCode(request.recipientBankCode)
                ?: throw BankNotFoundException("수취 은행을 찾을 수 없습니다.")

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
    }
}
