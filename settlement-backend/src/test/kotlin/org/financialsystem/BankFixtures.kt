package org.financialsystem

import org.financialsystem.settlement.bank.domain.Bank
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.springframework.test.util.ReflectionTestUtils

class BankFixtures {
    companion object {
        private var bankId = 0L

        fun bank(
            bankCode: String,
            balance: Long = 0L,
        ): Bank =
            Bank.create(bankCode, "http://$bankCode.com").apply {
                ReflectionTestUtils.setField(this, "id", ++bankId)
                credit(PositiveAmount(balance))
            }
    }
}
