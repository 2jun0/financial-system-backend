package org.financialsystem.bank.account.infrastructure

import org.financialsystem.bank.account.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface AccountRepository : JpaRepository<Account, Long> {
    fun findByAccountNumber(accountNumber: String): Account?

    fun findAllByCustomerId(customerId: Long): List<Account>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        UPDATE Account account
        SET account.balance = account.balance - :amount,
            account.updatedAt = :updatedAt
        WHERE account.accountNumber = :accountNumber
            AND account.customerId = :customerId
            AND account.balance >= :amount
        """,
    )
    fun debitIfSufficientBalance(
        @Param("accountNumber") accountNumber: String,
        @Param("customerId") customerId: Long,
        @Param("amount") amount: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}
