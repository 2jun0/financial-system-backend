package org.financialsystem.bank.account.application

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.bank.account.application.dto.CreateAccountRequest
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {
    @Mock
    lateinit var accountRepository: AccountRepository

    @Mock
    lateinit var accountNumberGenerator: AccountNumberGenerator

    @Test
    fun `계좌를 생성하면 생성된 계좌번호로 Account 객체를 반환한다`() {
        val customerId = 1L
        val name = "월급 계좌"
        val accountNumber = "1234567890"
        val createAccountRequest = CreateAccountRequest(name)
        val accountService = AccountService(accountRepository, accountNumberGenerator)
        given(accountNumberGenerator.generate()).willReturn(accountNumber)
        given(accountRepository.save(any(Account::class.java)))
            .willAnswer { param -> param.getArgument(0) }

        val account = accountService.createAccount(customerId, createAccountRequest)

        assertThat(account.customerId).isEqualTo(customerId)
        assertThat(account.name).isEqualTo(name)
        assertThat(account.accountNumber).isEqualTo(accountNumber)
    }

    @Test
    fun `고객 ID에 해당하는 계좌 목록을 반환한다`() {
        val customerId = 1L
        val accounts =
            listOf(
                Account.create("account-number-1", customerId, "생활비 계좌"),
                Account.create("account-number-2", customerId, "저축 계좌"),
            )
        val accountService = AccountService(accountRepository, accountNumberGenerator)
        given(accountRepository.findAllByCustomerId(customerId)).willReturn(accounts)

        val result = accountService.getAllAccount(customerId)

        assertThat(result).containsExactlyInAnyOrderElementsOf(accounts)
    }
}
