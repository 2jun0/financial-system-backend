package org.financialsystem.bank.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.support.persistence.IdCreatedAtEntity

@Entity
@Table(
    name = "account_transactions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_account_transactions_transfer_key_type", columnNames = ["transfer_key", "type"]),
    ],
    indexes = [
        Index(name = "idx_account_transactions_account_id", columnList = "account_id"),
    ],
)
class AccountTransaction private constructor(
    accountId: Long,
    transferKey: String,
    otherBankCode: String,
    otherAccountNumber: String,
    amount: Long,
    type: AccountTransactionType,
) : IdCreatedAtEntity() {
    init {
        when (type) {
            AccountTransactionType.DEBIT,
            -> require(amount < 0) { "DEBIT 거래 금액은 0보다 작아야 합니다. 입력값: $amount" }

            AccountTransactionType.CREDIT,
            AccountTransactionType.COMPENSATION,
            -> require(amount > 0) { "$type 거래 금액은 0보다 커야 합니다. 입력값: $amount" }
        }
    }

    @Column(name = "account_id", nullable = false, updatable = false)
    var accountId: Long = accountId
        protected set

    @Column(name = "transfer_key", nullable = false, updatable = false)
    var transferKey: String = transferKey
        protected set

    @Column(name = "other_bank_code", nullable = false, updatable = false)
    var otherBankCode: String = otherBankCode
        protected set

    @Column(name = "other_account_number", nullable = false, updatable = false)
    var otherAccountNumber: String = otherAccountNumber
        protected set

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Long = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    var type: AccountTransactionType = type
        protected set

    companion object {
        fun recordDebit(
            accountId: Long,
            transferKey: String,
            recipientBankCode: String,
            recipientAccountNumber: String,
            amount: PositiveAmount,
        ) = AccountTransaction(
            accountId,
            transferKey,
            recipientBankCode,
            recipientAccountNumber,
            -amount.value,
            AccountTransactionType.DEBIT,
        )

        fun recordCredit(
            accountId: Long,
            transferKey: String,
            sourceBankCode: String,
            sourceAccountNumber: String,
            amount: PositiveAmount,
        ) = AccountTransaction(
            accountId,
            transferKey,
            sourceBankCode,
            sourceAccountNumber,
            amount.value,
            AccountTransactionType.CREDIT,
        )

        fun recordCompensation(
            accountId: Long,
            transferKey: String,
            recipientBankCode: String,
            recipientAccountNumber: String,
            amount: PositiveAmount,
        ) = AccountTransaction(
            accountId,
            transferKey,
            recipientBankCode,
            recipientAccountNumber,
            amount.value,
            AccountTransactionType.COMPENSATION,
        )
    }
}
