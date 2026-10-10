package org.financialsystem.bank.support

import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.transfer.domain.Transfer
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class TransferFixtures {
    companion object {
        private var transferId = 0L

        fun transfer(
            sourceAccountId: Long,
            createdAt: Instant? = null,
            amount: Long = 10_000L,
            recipientBankCode: String = "BANK_B",
            recipientAccountNumber: String = "BANK_B_ACCOUNT",
        ): Transfer {
            val id = ++transferId

            return Transfer
                .initiate(
                    transferKey = "transfer-$id",
                    sourceAccountId = sourceAccountId,
                    recipientBankCode = recipientBankCode,
                    recipientAccountNumber = recipientAccountNumber,
                    amount = PositiveAmount(amount),
                ).apply {
                    ReflectionTestUtils.setField(this, "id", id)
                    createdAt?.let {
                        ReflectionTestUtils.setField(this, "createdAt", it)
                    }
                }
        }
    }
}
