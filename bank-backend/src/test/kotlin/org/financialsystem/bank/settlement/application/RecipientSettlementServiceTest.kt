package org.financialsystem.bank.settlement.application

import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.financialsystem.bank.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.bank.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.bank.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class RecipientSettlementServiceTest {
    @Mock
    lateinit var settlementClient: SettlementClient

    @Mock
    lateinit var accountRepository: AccountRepository

    private val bankCode = "BANK_B"
    private val firstSettlement =
        PendingSettlementResponse("first-key", "BANK_A", "source-account", bankCode, "first-account", 1_000L)
    private val nextSettlement = firstSettlement.copy(transferKey = "next-key", recipientAccountNumber = "next-account")
    private val recipientAccount = Account.create(firstSettlement.recipientAccountNumber, 1L, "수취 계좌")
    private val failure = SettlementClientException("중앙 결제 통신 실패", RuntimeException("응답 시간 초과"))

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `승인 또는 거절 전달이 실패해도 다음 결제를 처리한다`(accountExists: Boolean) {
        val service = RecipientSettlementService(settlementClient, accountRepository, bankCode)
        given(settlementClient.findPendingSettlements(bankCode)).willReturn(listOf(firstSettlement, nextSettlement))
        given(accountRepository.findByAccountNumber(firstSettlement.recipientAccountNumber))
            .willReturn(if (accountExists) recipientAccount else null)

        if (accountExists) {
            given(settlementClient.accept(firstSettlement.transferKey, AcceptSettlementRequest(bankCode))).willThrow(
                failure,
            )
        } else {
            given(settlementClient.reject(firstSettlement.transferKey, RejectSettlementRequest(bankCode))).willThrow(
                failure,
            )
        }

        service.decidePendingSettlements()

        verify(settlementClient).reject(nextSettlement.transferKey, RejectSettlementRequest(bankCode))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `계좌가 없으면 결제를 거절하고 계좌가 있으면 결제를 승인한다`(accountExists: Boolean) {
        val service = RecipientSettlementService(settlementClient, accountRepository, bankCode)
        given(settlementClient.findPendingSettlements(bankCode)).willReturn(listOf(firstSettlement))
        given(accountRepository.findByAccountNumber(firstSettlement.recipientAccountNumber))
            .willReturn(if (accountExists) recipientAccount else null)

        service.decidePendingSettlements()

        if (accountExists) {
            verify(settlementClient).accept(firstSettlement.transferKey, AcceptSettlementRequest(bankCode))
        } else {
            verify(settlementClient).reject(firstSettlement.transferKey, RejectSettlementRequest(bankCode))
        }
    }
}
