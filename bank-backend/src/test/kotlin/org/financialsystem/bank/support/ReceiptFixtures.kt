package org.financialsystem.bank.support

import org.financialsystem.bank.money.domain.PositiveAmount
import org.financialsystem.bank.receipt.domain.Receipt
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class ReceiptFixtures {
    companion object {
        private var receiptId = 0L

        fun receipt(
            transferKey: String,
            recipientAccountId: Long,
            sourceBankCode: String = "BANK_A",
            sourceAccountNumber: String = "BANK_A_ACCOUNT",
            amount: Long = 10_000L,
            transferCreatedAt: Instant = Instant.parse("2026-10-04T00:00:00Z"),
            createdAt: Instant? = null,
        ): Receipt =
            Receipt
                .initiate(
                    transferKey = transferKey,
                    recipientAccountId = recipientAccountId,
                    sourceBankCode = sourceBankCode,
                    sourceAccountNumber = sourceAccountNumber,
                    amount = PositiveAmount(amount),
                    transferCreatedAt = transferCreatedAt,
                ).apply {
                    ReflectionTestUtils.setField(this, "id", ++receiptId)
                    createdAt?.let {
                        ReflectionTestUtils.setField(this, "createdAt", it)
                    }
                }
    }
}
