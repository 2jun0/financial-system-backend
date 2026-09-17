package org.financialsystem.bank.receipt.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.support.persistence.IdCreatedAtEntity
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "receipts",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_receipts_transfer_key", columnNames = ["transfer_key"]),
    ],
    indexes = [
        Index(name = "idx_receipts_recipent_account_id", columnList = "recipent_account_id"),
    ],
)
class Receipt private constructor(
    transferKey: String,
    recipientAccountId: Long,
    sourceBankCode: String,
    sourceAccountNumber: String,
    amount: PositiveAmount,
    state: ReceiptState,
) : IdCreatedAtEntity() {
    @Column(name = "transfer_key", nullable = false, updatable = false)
    var transferKey: String = transferKey
        protected set

    @Column(name = "recipient_account_id", nullable = false, updatable = false)
    var recipientAccountId: Long = recipientAccountId
        protected set

    @Column(name = "source_bank_code", nullable = false, updatable = false)
    var sourceBankCode: String = sourceBankCode
        protected set

    @Column(name = "source_account_number", nullable = false, updatable = false)
    var sourceAccountNumber: String = sourceAccountNumber
        protected set

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Long = amount.value
        protected set

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    var state: ReceiptState = state
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun initiate(
            transferKey: String,
            recipientAccountId: Long,
            sourceBankCode: String,
            sourceAccountNumber: String,
            amount: PositiveAmount,
        ) = Receipt(
            transferKey,
            recipientAccountId,
            sourceBankCode,
            sourceAccountNumber,
            amount,
            ReceiptState.RECEIVING,
        )
    }

    fun succeed() {
        check(state == ReceiptState.RECEIVING) {
            "수취중인 거래만 성공 처리할 수 있습니다. 현재 상태: $state"
        }
        state = ReceiptState.SUCCEEDED
    }

    fun fail() {
        check(state == ReceiptState.RECEIVING) {
            "수취중인 거래만 실패 처리할 수 있습니다. 현재 상태: $state"
        }
        state = ReceiptState.FAILED
    }
}
