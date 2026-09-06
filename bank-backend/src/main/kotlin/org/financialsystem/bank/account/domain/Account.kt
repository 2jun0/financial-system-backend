package org.financialsystem.bank.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.bank.account.domain.exception.InsufficientBalanceException
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.support.persistence.IdCreatedAtEntity
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "accounts",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_accounts_account_number", columnNames = ["account_number"]),
    ],
    indexes = [
        Index(name = "idx_accounts_customer_id", columnList = "customer_id"),
    ],
)
class Account private constructor(
    accountNumber: String,
    customerId: Long,
    name: String,
) : IdCreatedAtEntity() {
    @Column(name = "account_number", nullable = false, updatable = false)
    var accountNumber = accountNumber
        protected set

    @Column(name = "customer_id", nullable = false)
    var customerId: Long = customerId
        protected set

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "balance", nullable = false)
    var balance: Long = 0
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun create(
            accountNumber: String,
            customerId: Long,
            name: String,
        ): Account = Account(accountNumber, customerId, name)
    }

    fun debit(amount: PositiveAmount) {
        if (balance < amount.value) {
            throw InsufficientBalanceException("잔액이 부족합니다. 현재 잔액: $balance, 출금액: $amount")
        }
        balance -= amount.value
    }

    fun credit(amount: PositiveAmount) {
        balance = Math.addExact(balance, amount.value)
    }
}
