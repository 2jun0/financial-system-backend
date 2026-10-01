package org.financialsystem.settlement.bank.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.settlement.bank.exception.InsufficientBalanceException
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.support.persistence.IdCreatedAtEntity
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "banks",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_banks_bank_code", columnNames = ["bank_code"]),
    ],
)
class Bank private constructor(
    bankCode: String,
    baseUri: String,
) : IdCreatedAtEntity() {
    @Column(name = "bank_code", nullable = false, updatable = false)
    var bankCode: String = bankCode
        protected set

    @Column(name = "balance", nullable = false)
    var balance: Long = 0L
        protected set

    @Column(name = "base_uri", nullable = false)
    var baseUri: String = baseUri
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun create(
            bankCode: String,
            baseUri: String,
        ): Bank = Bank(bankCode, baseUri)
    }

    fun debit(amount: PositiveAmount) {
        if (balance < amount.value) {
            throw InsufficientBalanceException("잔액이 부족합니다. 현재 잔액: $balance, 출금액: ${amount.value}")
        }
        balance -= amount.value
    }

    fun credit(amount: PositiveAmount) {
        balance = Math.addExact(balance, amount.value)
    }
}
