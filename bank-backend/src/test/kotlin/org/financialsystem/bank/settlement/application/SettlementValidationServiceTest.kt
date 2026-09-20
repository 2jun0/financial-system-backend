package org.financialsystem.bank.settlement.application

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.infrastructure.ReceiptRepository
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class SettlementValidationServiceTest {
    @Mock
    lateinit var receiptRepository: ReceiptRepository

    @Mock
    lateinit var accountRepository: AccountRepository

    private val transferKey = "transfer-key"
    private val recipientAccountId = 1L
    private val recipientAccountNumber = "BANK_B_ACCOUNT"
    private val sourceBankCode = "BANK_A"
    private val sourceAccountNumber = "BANK_A_ACCOUNT"
    private val amount = PositiveAmount(10_000L)
    private lateinit var settlementValidationService: SettlementValidationService

    @BeforeEach
    fun setUp() {
        settlementValidationService = SettlementValidationService(accountRepository, receiptRepository)
    }

    @Test
    fun `수취 계좌가 없으면 수취 불가를 응답하고 수취 기록을 생성하지 않는다`() {
        val request = createRequest()
        given(accountRepository.findByAccountNumber(recipientAccountNumber)).willReturn(null)

        val response = settlementValidationService.validateSettlement(request)

        assertThat(response.isAccepted).isFalse()
        verifyNoInteractions(receiptRepository)
    }

    @Test
    fun `수취 계좌가 있으면 수취 가능을 응답하고 수취 기록을 생성한다`() {
        val request = createRequest()
        val account = createAccount()
        given(accountRepository.findByAccountNumber(recipientAccountNumber)).willReturn(account)
        given(receiptRepository.existsByTransferKey(transferKey)).willReturn(false)

        val response = settlementValidationService.validateSettlement(request)

        val receiptCaptor = ArgumentCaptor.forClass(Receipt::class.java)
        verify(receiptRepository).save(receiptCaptor.capture())
        val receipt = receiptCaptor.value
        assertThat(response.isAccepted).isTrue()
        assertThat(receipt.transferKey).isEqualTo(transferKey)
        assertThat(receipt.recipientAccountId).isEqualTo(recipientAccountId)
        assertThat(receipt.sourceBankCode).isEqualTo(sourceBankCode)
        assertThat(receipt.sourceAccountNumber).isEqualTo(sourceAccountNumber)
        assertThat(receipt.amount).isEqualTo(amount.value)
    }

    @Test
    fun `같은 송금 키의 수취 기록이 있으면 새 수취 기록을 생성하지 않는다`() {
        val request = createRequest()
        val account = createAccount()
        given(accountRepository.findByAccountNumber(recipientAccountNumber)).willReturn(account)
        given(receiptRepository.existsByTransferKey(transferKey)).willReturn(true)

        val response = settlementValidationService.validateSettlement(request)

        assertThat(response.isAccepted).isTrue()
        verify(receiptRepository).existsByTransferKey(transferKey)
        verifyNoMoreInteractions(receiptRepository)
    }

    private fun createRequest(): ValidateSettlementRequest =
        ValidateSettlementRequest(
            transferKey = transferKey,
            sourceBankCode = sourceBankCode,
            sourceAccountNumber = sourceAccountNumber,
            recipientAccountNumber = recipientAccountNumber,
            amount = amount,
        )

    private fun createAccount(): Account =
        Account.create(recipientAccountNumber, customerId = 1L, name = "수취 계좌").also {
            ReflectionTestUtils.setField(it, "id", recipientAccountId)
        }
}
