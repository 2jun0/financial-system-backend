package org.financialsystem.settlement.settlement.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.support.persistence.IdCreatedAtEntity
import org.springframework.data.annotation.LastModifiedDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_settlements_transfer_key", columnNames = ["transfer_key"]),
    ],
    indexes = [
        Index(name = "idx_settlements_source_bank_id", columnList = "source_bank_id"),
        Index(name = "idx_settlements_recipient_bank_id", columnList = "recipient_bank_id"),
    ],
)
class Settlement private constructor(
    transferKey: String,
    sourceBankId: Long,
    sourceAccountNumber: String,
    recipientBankId: Long,
    recipientAccountNumber: String,
    amount: PositiveAmount,
    state: SettlementState,
) : IdCreatedAtEntity() {
    @Column(name = "transfer_key", nullable = false, updatable = false)
    var transferKey: String = transferKey
        protected set

    @Column(name = "source_bank_id", nullable = false, updatable = false)
    var sourceBankId: Long = sourceBankId
        protected set

    @Column(name = "source_account_number", nullable = false, updatable = false)
    var sourceAccountNumber: String = sourceAccountNumber
        protected set

    @Column(name = "recipient_bank_id", nullable = false, updatable = false)
    var recipientBankId: Long = recipientBankId
        protected set

    @Column(name = "recipient_account_number", nullable = false, updatable = false)
    var recipientAccountNumber: String = recipientAccountNumber
        protected set

    @Column(name = "amount", nullable = false)
    var amount: Long = amount.value
        protected set

    @Column(name = "state", nullable = false)
    @Enumerated(EnumType.STRING)
    var state: SettlementState = state
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    companion object {
        fun initiate(
            transferKey: String,
            sourceBankId: Long,
            sourceAccountNumber: String,
            recipientBankId: Long,
            recipientAccountNumber: String,
            amount: PositiveAmount,
        ) = Settlement(
            transferKey,
            sourceBankId,
            sourceAccountNumber,
            recipientBankId,
            recipientAccountNumber,
            amount,
            SettlementState.RECIPIENT_VALIDATION_PENDING,
        )
    }

    fun settle() {
        require(state == SettlementState.RECIPIENT_VALIDATION_PENDING)
        state = SettlementState.SETTLED
    }

    fun reject() {
        require(state == SettlementState.RECIPIENT_VALIDATION_PENDING)
        state = SettlementState.REJECTED
    }
}
