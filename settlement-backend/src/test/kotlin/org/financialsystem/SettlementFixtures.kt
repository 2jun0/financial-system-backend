package org.financialsystem

import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.settlement.domain.Settlement
import org.springframework.test.util.ReflectionTestUtils
import java.time.Instant

class SettlementFixtures {
    companion object {
        private var settlementId = 0L

        fun settlement(
            transferKey: String,
            sourceBankId: Long,
            recipientBankId: Long,
            sourceAccountNumber: String = "BANK_A_ACCOUNT",
            recipientAccountNumber: String = "BANK_B_ACCOUNT",
            amount: Long = 10_000L,
            transferCreatedAt: Instant = Instant.parse("2026-10-04T00:00:00Z"),
            createdAt: Instant? = null,
        ): Settlement =
            Settlement
                .createPending(
                    transferKey = transferKey,
                    sourceBankId = sourceBankId,
                    sourceAccountNumber = sourceAccountNumber,
                    recipientBankId = recipientBankId,
                    recipientAccountNumber = recipientAccountNumber,
                    amount = PositiveAmount(amount),
                    transferCreatedAt = transferCreatedAt,
                ).apply {
                    ReflectionTestUtils.setField(this, "id", ++settlementId)
                    createdAt?.let {
                        ReflectionTestUtils.setField(this, "createdAt", it)
                    }
                }
    }
}
