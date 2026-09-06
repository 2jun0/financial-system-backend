package org.financialsystem.bank.transfer.domain

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
    name = "transfers",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_transfers_transfer_key", columnNames = ["transfer_key"]),
    ],
    indexes = [
        Index(name = "idx_transfers_source_account_id", columnList = "source_account_id"),
    ],
)
class Transfer private constructor(
    transferKey: String,
    sourceAccountId: Long,
    recipientBankCode: String,
    recipientAccountNumber: String,
    amount: PositiveAmount,
    state: TransferState,
) : IdCreatedAtEntity() {
    @Column(name = "transfer_key", nullable = false, updatable = false)
    var transferKey: String = transferKey
        protected set

    @Column(name = "source_account_id", nullable = false, updatable = false)
    var sourceAccountId: Long = sourceAccountId
        protected set

    @Column(name = "recipient_bank_code", nullable = false, updatable = false)
    var recipientBankCode: String = recipientBankCode
        protected set

    @Column(name = "recipient_account_number", nullable = false, updatable = false)
    var recipientAccountNumber: String = recipientAccountNumber
        protected set

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Long = amount.value
        protected set

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    var state: TransferState = state
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun initiate(
            transferKey: String,
            sourceAccountId: Long,
            recipientBankCode: String,
            recipientAccountNumber: String,
            amount: PositiveAmount,
        ) = Transfer(
            transferKey,
            sourceAccountId,
            recipientBankCode,
            recipientAccountNumber,
            amount,
            TransferState.TRANSFERRING,
        )
    }

    fun succeed() {
        check(state == TransferState.TRANSFERRING) {
            "송금중인 거래만 성공 처리할 수 있습니다. 현재 상태: $state"
        }
        state = TransferState.SUCCEEDED
    }

    fun fail() {
        check(state == TransferState.TRANSFERRING) {
            "송금중인 거래만 실패 처리할 수 있습니다. 현재 상태: $state"
        }
        state = TransferState.FAILED
    }
}
