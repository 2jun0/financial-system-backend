package org.financialsystem.bank.receipt.infrastructure

import org.financialsystem.bank.receipt.domain.Receipt
import org.financialsystem.bank.receipt.domain.ReceiptState
import org.springframework.data.jpa.repository.JpaRepository

interface ReceiptRepository : JpaRepository<Receipt, Long> {
    fun findAllByState(state: ReceiptState): List<Receipt>
}
