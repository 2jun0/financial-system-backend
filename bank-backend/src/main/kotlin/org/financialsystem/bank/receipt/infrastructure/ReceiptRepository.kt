package org.financialsystem.bank.receipt.infrastructure

import org.financialsystem.bank.receipt.domain.Receipt
import org.springframework.data.jpa.repository.JpaRepository

interface ReceiptRepository : JpaRepository<Receipt, Long> {
    fun findByTransferKey(transferKey: String): Receipt?

    fun existsByTransferKey(transferKey: String): Boolean
}
