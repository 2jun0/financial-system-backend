package org.financialsystem.bank.account.application

import org.financialsystem.bank.account.application.dto.CreateAccountRequest
import org.financialsystem.bank.account.domain.Account
import org.financialsystem.bank.account.infrastructure.AccountRepository
import org.springframework.stereotype.Service

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
) {
    fun createAccount(
        customerId: Long,
        createAccountRequest: CreateAccountRequest,
    ): Account {
        val account =
            Account.create(
                accountNumberGenerator.generate(),
                customerId,
                createAccountRequest.name,
            )

        return accountRepository.save(account)
    }
}
