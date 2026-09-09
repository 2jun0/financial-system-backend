package org.financialsystem.settlement.bank.infrastructure

import org.financialsystem.settlement.bank.domain.Bank
import org.springframework.data.jpa.repository.JpaRepository

interface BankRepository : JpaRepository<Bank, Long> {
    fun findByBankCode(bankCode: String): Bank?
}
