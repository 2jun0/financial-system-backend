package org.financialsystem.bank.account.infrastructure

import org.financialsystem.bank.account.domain.AccountTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface AccountTransactionRepository : JpaRepository<AccountTransaction, Long>
