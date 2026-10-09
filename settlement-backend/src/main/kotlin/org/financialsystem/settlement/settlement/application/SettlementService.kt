package org.financialsystem.settlement.settlement.application

import org.financialsystem.settlement.bank.domain.Bank
import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementResponse
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.settlement.settlement.application.exception.RecipientBankClientException
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.SameBankSettlementException
import org.financialsystem.settlement.settlement.exception.SettlementNotFoundException
import org.financialsystem.settlement.settlement.exception.SourceBankMismatchException
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Duration
import java.time.Instant

val SETTLEMENT_TIME_LIMIT = Duration.ofSeconds(300)

@Service
class SettlementService(
    private val clock: Clock,
    private val bankRepository: BankRepository,
    private val settlementRepository: SettlementRepository,
    private val recipientBankClient: RecipientBankClient,
    private val transactionOperations: TransactionOperations,
) {
    private val logger = LoggerFactory.getLogger(SettlementService::class.java)

    fun requestSettlement(request: SettlementRequest): SettlementResponse {
        if (request.sourceBankCode == request.recipientBankCode) {
            throw SameBankSettlementException("동일 은행 간 결제는 할 수 없습니다.")
        }

        val sourceBank =
            bankRepository.findByBankCode(request.sourceBankCode)
                ?: throw BankNotFoundException("송금 은행을 찾을 수 없습니다.")
        val recipientBank =
            bankRepository.findByBankCode(request.recipientBankCode)
                ?: throw BankNotFoundException("수취 은행을 찾을 수 없습니다.")

        val settlementToReturn =
            transactionOperations.execute {
                checkExistingSettlement(request, sourceBank.id!!, recipientBank.id!!)
                    ?.let {
                        return@execute it
                    }

                prepareSettlement(request, sourceBank.id!!, recipientBank.id!!)
                    .let {
                        if (it.state == SettlementState.REJECTED) return@execute it
                    }
                null
            }
        settlementToReturn?.let {
            return SettlementResponse.from(it)
        }

        val isValidationPass =
            validateFromRecipientBank(
                recipientBank,
                request,
            ) &&
                validateTimeLimit(request.transferCreatedAt)

        val response =
            transactionOperations.execute {
                applySettlement(request.transferKey, isValidationPass)
            }
        notifySettlementResult(recipientBank.baseUri, response)

        return response
    }

    private fun checkExistingSettlement(
        request: SettlementRequest,
        sourceBankId: Long,
        recipientBankId: Long,
    ): Settlement? {
        val existingSettlement = settlementRepository.findByTransferKey(request.transferKey)
        if (existingSettlement != null) {
            if (!existingSettlement.matchesRequest(
                    sourceBankId = sourceBankId,
                    sourceAccountNumber = request.sourceAccountNumber,
                    recipientBankId = recipientBankId,
                    recipientAccountNumber = request.recipientAccountNumber,
                    amount = request.amount,
                )
            ) {
                throw TransferKeyDuplicatedException()
            }

            return existingSettlement
        }

        return null
    }

    private fun prepareSettlement(
        request: SettlementRequest,
        sourceBankId: Long,
        recipientBankId: Long,
    ): Settlement =
        Settlement
            .createPending(
                request.transferKey,
                sourceBankId,
                request.sourceAccountNumber,
                recipientBankId,
                request.recipientAccountNumber,
                request.amount,
                request.transferCreatedAt,
            ).apply {
                if (!validateTimeLimit(request.transferCreatedAt)) {
                    reject()
                }
            }.also(settlementRepository::save)

    private fun validateFromRecipientBank(
        recipientBank: Bank,
        request: SettlementRequest,
    ): Boolean {
        try {
            return recipientBankClient
                .validateSettlement(
                    recipientBank.baseUri,
                    ValidateSettlementRequest(
                        request.transferKey,
                        request.sourceBankCode,
                        request.sourceAccountNumber,
                        request.recipientAccountNumber,
                        request.amount,
                        request.transferCreatedAt,
                    ),
                ).isAccepted
        } catch (exception: RecipientBankClientException) {
            logger.debug("수취 검증 실패", exception)
            return false
        }
    }

    private fun applySettlement(
        transferKey: String,
        isAccepted: Boolean,
    ): SettlementResponse {
        val settlement =
            settlementRepository.findByTransferKey(transferKey)
                ?: error("결제를 찾을 수 없습니다. transferKey:$transferKey")

        if (isAccepted) {
            val sourceBank =
                bankRepository.findByIdOrNull(settlement.sourceBankId)
                    ?: error("송금 은행을 찾을 수 없습니다. id:${settlement.sourceBankId}")
            val recipientBank =
                bankRepository.findByIdOrNull(settlement.recipientBankId)
                    ?: error("수취 은행을 찾을 수 없습니다. id:${settlement.recipientBankId}")

            if (sourceBank.balance >= settlement.amount) {
                settlement.settle()
                val amount = PositiveAmount(settlement.amount)
                sourceBank.debit(amount)
                recipientBank.credit(amount)
            } else {
                settlement.reject()
            }
        } else {
            settlement.reject()
        }

        return SettlementResponse.from(settlement)
    }

    private fun validateTimeLimit(transferCreatedAt: Instant): Boolean =
        Instant.now(clock).isBefore(transferCreatedAt.plus(SETTLEMENT_TIME_LIMIT))

    private fun notifySettlementResult(
        recipientBankUri: String,
        response: SettlementResponse,
    ) {
        try {
            when (response.state) {
                SettlementState.SETTLED -> {
                    recipientBankClient.notifySettledResult(
                        recipientBankUri,
                        response.transferKey,
                    )
                }

                SettlementState.REJECTED -> {
                    recipientBankClient.notifyRejectedResult(
                        recipientBankUri,
                        response.transferKey,
                    )
                }

                SettlementState.RECIPIENT_VALIDATION_PENDING -> {
                    error("잘못된 상태 통지입니다: ${response.state}")
                }
            }
        } catch (exception: RecipientBankClientException) {
            logger.debug("결제 결과 통지 실패", exception)
        }
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

    private fun validateSourceBank(
        requestedBankCode: String,
        sourceBankCode: String,
    ) {
        if (requestedBankCode != sourceBankCode) {
            throw SourceBankMismatchException("지정된 송금 은행만 결제를 조회할 수 있습니다.")
        }
    }

    @Transactional(readOnly = true)
    fun batchGetSettlementStates(
        bankCode: String,
        transferKeys: List<String>,
    ): BatchGetSettlementStatesResponse {
        val bank =
            bankRepository.findByBankCode(bankCode)
                ?: throw BankNotFoundException("존재하지 않는 은행 코드입니다: $bankCode")

        val settlements =
            settlementRepository.findAllByBankIdAndTransferKeyIn(
                bankId = bank.id!!,
                transferKeys = transferKeys,
            )

        return BatchGetSettlementStatesResponse.from(settlements)
    }
}
