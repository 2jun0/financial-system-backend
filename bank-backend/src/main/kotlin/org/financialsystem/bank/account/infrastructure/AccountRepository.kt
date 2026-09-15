package org.financialsystem.bank.account.infrastructure

import org.financialsystem.bank.account.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByAccountNumber(accountNumber: String): Account?

    fun findAllByCustomerId(customerId: Long): List<Account>
}
