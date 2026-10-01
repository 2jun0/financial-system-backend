package org.financialsystem.bank.transfer.infrastructure

import org.financialsystem.bank.transfer.domain.Transfer
import org.springframework.data.jpa.repository.JpaRepository

interface TransferRepository : JpaRepository<Transfer, Long> {
    fun findByTransferKey(transferKey: String): Transfer?
}
