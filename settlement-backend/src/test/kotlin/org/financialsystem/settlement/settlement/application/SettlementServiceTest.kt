package org.financialsystem.settlement.settlement.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.settlement.bank.domain.Bank
import org.financialsystem.settlement.bank.exception.BankNotFoundException
import org.financialsystem.settlement.bank.infrastructure.BankRepository
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.settlement.settlement.domain.Settlement
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.exception.RecipientBankMismatchException
import org.financialsystem.settlement.settlement.exception.TransferKeyDuplicatedException
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class SettlementServiceTest {
    private val bankA = bank(id = 1L, bankCode = "BANK_A")
    private val bankB = bank(id = 2L, bankCode = "BANK_B")
    private val bankC = bank(id = 3L, bankCode = "BANK_C")

    @Mock
    lateinit var settlementRepository: SettlementRepository

    @Mock
    lateinit var bankRepository: BankRepository

    private lateinit var settlementService: SettlementService

    @BeforeEach
    fun setUp() {
        settlementService = SettlementService(bankRepository, settlementRepository)
    }

    @Test
    fun `은행 간 결제를 개시하면 settlement 가 저장된다`() {
        val initiateSettlementRequest = initiateSettlementRequest(amount = 10_000)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)

        val response = settlementService.initiate(initiateSettlementRequest)

        val captor = ArgumentCaptor.forClass(Settlement::class.java)

        verify(settlementRepository).save(captor.capture())
        val savedSettlement = captor.value
        assertThat(savedSettlement.transferKey)
            .isEqualTo(initiateSettlementRequest.transferKey)
        assertThat(savedSettlement.sourceBankId).isEqualTo(1L)
        assertThat(savedSettlement.recipientBankId).isEqualTo(2L)
        assertThat(savedSettlement.amount)
            .isEqualTo(initiateSettlementRequest.amount.value)
        assertThat(savedSettlement.state)
            .isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
        assertThat(response.transferKey).isEqualTo("transferKey")
        assertThat(response.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
    }

    @Test
    fun `은행 간 결제를 개시할 때 송신 은행이 존재하지 않으면 예외를 던진다`() {
        val initiateSettlementRequest = initiateSettlementRequest(amount = 10_000)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(null)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            BankNotFoundException::class.java,
        )
    }

    @Test
    fun `은행 간 결제를 개시할 때 수취 은행이 존재하지 않으면 예외를 던진다`() {
        val initiateSettlementRequest = initiateSettlementRequest(amount = 10_000)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(null)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            BankNotFoundException::class.java,
        )
    }

    @Test
    fun `동일한 결제 개시 요청은 새로 생성하지 않고 기존의 결제를 반환한다`() {
        val initiateSettlementRequest = initiateSettlementRequest(amount = 10_000)
        val existingSettlement = pendingSettlement(amount = 10_000)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
        given(settlementRepository.findByTransferKey("transferKey")).willReturn(existingSettlement)

        val response = settlementService.initiate(initiateSettlementRequest)

        assertThat(response.transferKey).isEqualTo("transferKey")
        assertThat(response.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
    }

    @Test
    fun `같은 송금 키로 다른 결제를 개시하면 예외를 던진다`() {
        val initiateSettlementRequest = initiateSettlementRequest(amount = 20_000)
        val existingSettlement = pendingSettlement(amount = 10_000)
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(bankRepository.findByBankCode("BANK_B")).willReturn(bankB)
        given(settlementRepository.findByTransferKey("transferKey")).willReturn(existingSettlement)

        assertThatThrownBy { settlementService.initiate(initiateSettlementRequest) }.isInstanceOf(
            TransferKeyDuplicatedException::class.java,
        )
    }

    @Test
    fun `수취 검증 대기 상태의 결제를 조회한다`() {
        val pendingSettlements =
            mutableListOf(
                pendingSettlement(
                    transferKey = "transferKey-1",
                    sourceBank = bankB,
                    recipientBank = bankA,
                    amount = 10_000,
                ),
                pendingSettlement(
                    transferKey = "transferKey-2",
                    sourceBank = bankC,
                    recipientBank = bankA,
                    amount = 20_000,
                ),
            )
        given(bankRepository.findByBankCode("BANK_A")).willReturn(bankA)
        given(
            settlementRepository.findByRecipientBankIdAndState(
                1L,
                SettlementState.RECIPIENT_VALIDATION_PENDING,
                PageRequest.of(0, 100, Sort.by("createdAt", "id").ascending()),
            ),
        ).willReturn(pendingSettlements)
        given(bankRepository.findAllById(setOf(2L, 3L))).willReturn(listOf(bankB, bankC))

        val responses = settlementService.findPendingSettlements("BANK_A")

        assertThat(responses).containsExactly(
            PendingSettlementResponse("transferKey-1", "BANK_B", "BANK_B_ACCOUNT", "BANK_A", "BANK_A_ACCOUNT", 10_000),
            PendingSettlementResponse("transferKey-2", "BANK_C", "BANK_C_ACCOUNT", "BANK_A", "BANK_A_ACCOUNT", 20_000),
        )
    }

    @Test
    fun `수취 검증 대기 상태의 결제를 조회할 때 수취 은행이 존재하지 않으면 예외를 던진다`() {
        given(bankRepository.findByBankCode("BANK_A")).willReturn(null)

        assertThatThrownBy { settlementService.findPendingSettlements("BANK_A") }
            .isInstanceOf(BankNotFoundException::class.java)
        verifyNoInteractions(settlementRepository)
    }

    @Test
    fun `결제를 승인하면 송금 은행에서 수취 은행으로 잔액이 이동하고 결제완료 상태가 된다`() {
        bankA.credit(PositiveAmount(10_000L))
        given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(transferKey = "transfer-key", amount = 1_000)
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = AcceptSettlementRequest(bankB.bankCode)

        val response = settlementService.accept(settlement.transferKey, request)

        assertThat(bankA.balance).isEqualTo(9_000L)
        assertThat(bankB.balance).isEqualTo(1_000L)
        assertThat(settlement.state).isEqualTo(SettlementState.SETTLED)
        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.SETTLED)
    }

    @Test
    fun `결제를 승인할 때 잔액이 부족한 경우 잔액을 이동하지 않고 결제거절 상태가 된다`() {
        given(bankRepository.findById(bankA.id!!)).willReturn(Optional.of(bankA))
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(transferKey = "transfer-key", amount = 1_000)
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = AcceptSettlementRequest(bankB.bankCode)

        val response = settlementService.accept(settlement.transferKey, request)

        assertThat(bankA.balance).isEqualTo(0)
        assertThat(bankB.balance).isEqualTo(0)
        assertThat(settlement.state).isEqualTo(SettlementState.REJECTED)
        assertThat(response.state).isEqualTo(SettlementState.REJECTED)
    }

    @Test
    fun `결제를 승인할 때 지정된 수취 은행이 아니면 예외를 던진다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(transferKey = "transfer-key", amount = 1_000)
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = AcceptSettlementRequest(bankC.bankCode)

        assertThatThrownBy {
            settlementService.accept(settlement.transferKey, request)
        }.isInstanceOf(RecipientBankMismatchException::class.java)
    }

    @Test
    fun `결제를 거절하면 잔액을 이동하지 않고 결제거절 상태가 된다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(transferKey = "transfer-key", amount = 1_000)
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = RejectSettlementRequest(bankB.bankCode)

        val response = settlementService.reject(settlement.transferKey, request)

        assertThat(bankA.balance).isEqualTo(0)
        assertThat(bankB.balance).isEqualTo(0)
        assertThat(settlement.state).isEqualTo(SettlementState.REJECTED)
        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.REJECTED)
    }

    @Test
    fun `결제된 결제를 승인하면 결제 상태를 응답한다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(amount = 1_000).apply { this.settle() }
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = AcceptSettlementRequest(bankB.bankCode)

        val response = settlementService.accept(settlement.transferKey, request)

        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.SETTLED)
    }

    @Test
    fun `거절된 결제를 승인하면 결제 상태를 응답한다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(amount = 1_000).apply { this.reject() }
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = AcceptSettlementRequest(bankB.bankCode)

        val response = settlementService.accept(settlement.transferKey, request)

        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.REJECTED)
    }

    @Test
    fun `결제된 결제를 거절하면 결제 상태를 응답한다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(amount = 1_000).apply { this.settle() }
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = RejectSettlementRequest(bankB.bankCode)

        val response = settlementService.reject(settlement.transferKey, request)

        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.SETTLED)
    }

    @Test
    fun `거절된 결제를 거절하면 결제 상태를 응답한다`() {
        given(bankRepository.findById(bankB.id!!)).willReturn(Optional.of(bankB))
        val settlement = pendingSettlement(amount = 1_000).apply { this.reject() }
        given(settlementRepository.findByTransferKey(settlement.transferKey)).willReturn(settlement)
        val request = RejectSettlementRequest(bankB.bankCode)

        val response = settlementService.reject(settlement.transferKey, request)

        assertThat(response.transferKey).isEqualTo(settlement.transferKey)
        assertThat(response.state).isEqualTo(SettlementState.REJECTED)
    }

    private fun bank(
        id: Long,
        bankCode: String,
    ): Bank =
        Bank.create(bankCode).apply {
            ReflectionTestUtils.setField(this, "id", id)
        }

    private fun initiateSettlementRequest(amount: Long): InitiateSettlementRequest =
        InitiateSettlementRequest(
            transferKey = "transferKey",
            sourceBankCode = bankA.bankCode,
            sourceAccountNumber = "BANK_A_ACCOUNT",
            recipientBankCode = bankB.bankCode,
            recipientAccountNumber = "BANK_B_ACCOUNT",
            amount = PositiveAmount(amount),
        )

    private fun pendingSettlement(
        amount: Long,
        transferKey: String = "transferKey",
        sourceBank: Bank = bankA,
        recipientBank: Bank = bankB,
        sourceAccountNumber: String = "${sourceBank.bankCode}_ACCOUNT",
        recipientAccountNumber: String = "${recipientBank.bankCode}_ACCOUNT",
    ): Settlement =
        Settlement.initiate(
            transferKey = transferKey,
            sourceBankId = sourceBank.id!!,
            sourceAccountNumber = sourceAccountNumber,
            recipientBankId = recipientBank.id!!,
            recipientAccountNumber = recipientAccountNumber,
            amount = PositiveAmount(amount),
        )
}
