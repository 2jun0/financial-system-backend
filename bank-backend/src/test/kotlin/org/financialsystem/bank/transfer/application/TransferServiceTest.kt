package org.financialsystem.bank.transfer.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.application.exception.SameBankTransferNotSupportException
import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState
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

    private val settlementClient = FakeSettlementClient()

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
                settlementClient,
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
        assertThat(settlementClient.requests).containsExactly(
            InitiateSettlementRequest(
                transferKey = expectedTransfer.transferKey,
                sourceBankCode = bankCode,
                sourceAccountNumber = sourceAccountNumber,
                recipientBankCode = recipientBankCode,
                recipientAccountNumber = recipientAccountNumber,
                amount = amount,
            ),
        )
    }

    @Test
    fun `중앙 결제 통신이 실패해도 기존 송금 키와 송금중 상태를 반환한다`() {
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
                expectedTransfer.transferKey,
                customerId,
                sourceAccountNumber,
                transferRequest,
            ),
        ).willReturn(expectedTransfer)
        settlementClient.failWith(
            SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", RuntimeException("응답 시간 초과")),
        )

        val transfer = transferService.initiateTransfer(customerId, sourceAccountNumber, transferRequest)

        assertThat(transfer).isSameAs(expectedTransfer)
        assertThat(transfer.transferKey).isEqualTo(expectedTransfer.transferKey)
        assertThat(transfer.state).isEqualTo(TransferState.TRANSFERRING)
    }

    @Test
    fun `중앙 결제 요청 중 통신 실패가 아닌 예외는 그대로 전파한다`() {
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
                expectedTransfer.transferKey,
                customerId,
                sourceAccountNumber,
                transferRequest,
            ),
        ).willReturn(expectedTransfer)
        settlementClient.failWith(IllegalStateException("요청 직렬화 실패"))

        assertThatThrownBy {
            transferService.initiateTransfer(customerId, sourceAccountNumber, transferRequest)
        }.isInstanceOf(IllegalStateException::class.java)
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
        assertThat(settlementClient.requests).isEmpty()
    }
}

class FakeTransferKeyGenerator : TransferKeyGenerator {
    override fun generate(): String = "transfer_key"
}

class FakeSettlementClient : SettlementClient {
    val requests = mutableListOf<InitiateSettlementRequest>()
    private var failure: RuntimeException? = null

    fun failWith(exception: RuntimeException) {
        failure = exception
    }

    override fun initiate(request: InitiateSettlementRequest) {
        requests += request
        failure?.let { throw it }
    }
}
