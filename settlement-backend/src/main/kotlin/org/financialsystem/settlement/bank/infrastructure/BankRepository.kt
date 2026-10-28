package org.financialsystem.settlement.bank.infrastructure

import jakarta.persistence.LockModeType
import org.financialsystem.settlement.bank.domain.Bank
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface BankRepository : JpaRepository<Bank, Long> {
    fun findByBankCode(bankCode: String): Bank?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bank FROM Bank bank WHERE bank.id = :bankId")
    fun findByIdForUpdate(
        @Param("bankId") bankId: Long,
    ): Bank?

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Bank bank
        SET bank.balance = bank.balance - :amount,
            bank.updatedAt = :updatedAt
        WHERE bank.id = :bankId AND bank.balance >= :amount
        """,
    )
    fun debitIfSufficientBalance(
        @Param("bankId") bankId: Long,
        @Param("amount") amount: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int

    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE Bank bank
        SET bank.balance = bank.balance + :amount,
            bank.updatedAt = :updatedAt
        WHERE bank.id = :bankId
        """,
    )
    fun credit(
        @Param("bankId") bankId: Long,
        @Param("amount") amount: Long,
        @Param("updatedAt") updatedAt: Instant,
    ): Int
}
