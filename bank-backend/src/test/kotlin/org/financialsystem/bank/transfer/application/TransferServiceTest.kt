package org.financialsystem.bank.transfer.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class TransferServiceTest {
    @Mock
    lateinit var transferInitiationService: TransferInitiationService


    private val sourceAccountNumber = "001-source-account"
    private val fakeTransferKeyGenerator = FakeTransferKeyGenerator()
    private val bankCode = "BANK_A"
    private val customerId = 1L
    private val sourceAccountId = 10L
    private val recipientBankCode = "BANK_B"
    private val recipientAccountNumber = "recipient-account-number"
    private val amount = PositiveAmount(10_000L)
    private val transferRequest =
        TransferRequest(
            recipientBankCode = recipientBankCode,
            recipientAccountNumber = recipientAccountNumber,
            amount = amount,
        )
    private lateinit var transferService: TransferService

    @BeforeEach
    fun setUp() {
        transferService =
            TransferService(
                fakeTransferKeyGenerator,
                transferInitiationService,
                bankCode,
            )
    }

    @Test
    fun `송금을 개시하면 생성한 송금 키로 은행 송금을 등록한다`() {
        val expectedTransfer =
            Transfer.initiate(
                fakeTransferKeyGenerator.generate(),
                sourceAccountId,
                recipientBankCode,
                recipientAccountNumber,
                amount,
            )
        given(
            transferInitiationService.initiateTransfer(
                fakeTransferKeyGenerator.generate(),
                customerId,
                sourceAccountNumber,
                transferRequest,
            ),
        ).willReturn(expectedTransfer)

        val transfer =
            transferService.initiateTransfer(
                customerId,
                sourceAccountNumber,
                transferRequest,
            )

        assertThat(transfer).isSameAs(expectedTransfer)
    }

    @Test
    fun `같은 은행 송금은 은행 송금을 등록하지 않고 거절한다`() {
        assertThatThrownBy {
            transferService.initiateTransfer(
                customerId,
                sourceAccountNumber,
                transferRequest.copy(recipientBankCode = bankCode),
            )
        }.isInstanceOf(SameBankTransferNotSupportException::class.java)

        verifyNoInteractions(transferInitiationService)
    }
}

class FakeTransferKeyGenerator : TransferKeyGenerator {
    override fun generate(): String = "transfer_key"
}
