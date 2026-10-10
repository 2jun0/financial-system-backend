package org.financialsystem.settlement.settlement.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.settlement.money.domain.PositiveAmount
import org.financialsystem.settlement.support.SettlementFixtures
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import kotlin.test.Test

class SettlementTest {
    private val transferCreatedAt = Instant.parse("2026-10-04T00:00:00Z")

    @Nested
    inner class `결제를 생성한다` {
        @Test
        fun `입력된 값과 수취 대기 상태로 생성한다`() {
            val settlement =
                Settlement.createPending(
                    transferKey = "transfer-key",
                    sourceBankId = 1L,
                    sourceAccountNumber = "BANK_A_ACCOUNT",
                    recipientBankId = 2L,
                    recipientAccountNumber = "BANK_B_ACCOUNT",
                    amount = PositiveAmount(10_000L),
                    transferCreatedAt = transferCreatedAt,
                )

            assertThat(settlement.transferKey).isEqualTo("transfer-key")
            assertThat(settlement.sourceBankId).isEqualTo(1L)
            assertThat(settlement.sourceAccountNumber).isEqualTo("BANK_A_ACCOUNT")
            assertThat(settlement.recipientBankId).isEqualTo(2L)
            assertThat(settlement.recipientAccountNumber).isEqualTo("BANK_B_ACCOUNT")
            assertThat(settlement.amount).isEqualTo(10_000L)
            assertThat(settlement.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
        }
    }

    @Nested
    inner class `상태를 변경한다` {
        val pendingSettlement =
            SettlementFixtures.settlement(
                transferKey = "transfer-key",
                sourceBankId = 1L,
                recipientBankId = 2L,
            )

        @Test
        fun `대기 상태에서 결제하면 SETTLED로 설정한다`() {
            pendingSettlement.settle()

            assertThat(pendingSettlement.state).isEqualTo(SettlementState.SETTLED)
        }

        @Test
        fun `대기 상태에서 거절하면 REJECTED로 설정한다`() {
            pendingSettlement.reject()

            assertThat(pendingSettlement.state).isEqualTo(SettlementState.REJECTED)
        }

        @ParameterizedTest
        @EnumSource(
            value = SettlementState::class,
            names = ["SETTLED", "REJECTED"],
        )
        fun `완료된 상태에서 결제하면 예외를 던진다`(state: SettlementState) {
            val settlement = pendingSettlement
            when (state) {
                SettlementState.SETTLED -> settlement.settle()
                SettlementState.REJECTED -> settlement.reject()
                else -> error("잘못됨")
            }

            assertThatThrownBy { settlement.settle() }
                .isInstanceOf(IllegalArgumentException::class.java)

            assertThat(settlement.state).isEqualTo(state)
        }

        @ParameterizedTest
        @EnumSource(
            value = SettlementState::class,
            names = ["SETTLED", "REJECTED"],
        )
        fun `완료된 상태에서 거절하면 예외를 던진다`(state: SettlementState) {
            val settlement = pendingSettlement
            when (state) {
                SettlementState.SETTLED -> settlement.settle()
                SettlementState.REJECTED -> settlement.reject()
                else -> error("잘못됨")
            }

            assertThatThrownBy { settlement.reject() }
                .isInstanceOf(IllegalArgumentException::class.java)

            assertThat(settlement.state).isEqualTo(state)
        }
    }

    @Test
    fun `입력된 값과 수취 대기 상태로 생성된다`() {
        val settlement =
            Settlement.createPending(
                transferKey = "transfer-key",
                sourceBankId = 1L,
                sourceAccountNumber = "BANK_A_ACCOUNT",
                recipientBankId = 2L,
                recipientAccountNumber = "BANK_B_ACCOUNT",
                amount = PositiveAmount(10_000L),
                transferCreatedAt = transferCreatedAt,
            )

        assertThat(settlement.transferKey).isEqualTo("transfer-key")
        assertThat(settlement.sourceBankId).isEqualTo(1L)
        assertThat(settlement.sourceAccountNumber).isEqualTo("BANK_A_ACCOUNT")
        assertThat(settlement.recipientBankId).isEqualTo(2L)
        assertThat(settlement.recipientAccountNumber).isEqualTo("BANK_B_ACCOUNT")
        assertThat(settlement.amount).isEqualTo(10_000L)
        assertThat(settlement.state).isEqualTo(SettlementState.RECIPIENT_VALIDATION_PENDING)
    }

    @Nested
    inner class `요청 내용과 동일한지 비교한다` {
        val settlement =
            SettlementFixtures.settlement(
                transferKey = "transfer-key",
                sourceBankId = 1L,
                recipientBankId = 2L,
            )

        @Test
        fun `모든 파라미터 값이 같으면 true를 반환한다`() {
            val result =
                settlement.matchesRequest(
                    sourceBankId = 1L,
                    sourceAccountNumber = "BANK_A_ACCOUNT",
                    recipientBankId = 2L,
                    recipientAccountNumber = "BANK_B_ACCOUNT",
                    amount = PositiveAmount(10_000L),
                )

            assertThat(result).isTrue()
        }

        @Test
        fun `파라미터 값이 하나라도 다르면 false를 반환한다`() {
            val settlement =
                SettlementFixtures.settlement(
                    transferKey = "transfer-key",
                    sourceBankId = 1L,
                    sourceAccountNumber = "source-account",
                    recipientBankId = 2L,
                    recipientAccountNumber = "recipient-account",
                )

            fun matches(
                sourceBankId: Long = settlement.sourceBankId,
                sourceAccountNumber: String = settlement.sourceAccountNumber,
                recipientBankId: Long = settlement.recipientBankId,
                recipientAccountNumber: String = settlement.recipientAccountNumber,
                amount: PositiveAmount = PositiveAmount(settlement.amount),
            ) = settlement.matchesRequest(
                sourceBankId,
                sourceAccountNumber,
                recipientBankId,
                recipientAccountNumber,
                amount,
            )

            assertThat(matches(sourceBankId = 999L)).isFalse()
            assertThat(matches(sourceAccountNumber = "other-account")).isFalse()
            assertThat(matches(recipientBankId = 999L)).isFalse()
            assertThat(matches(recipientAccountNumber = "other-account")).isFalse()
            assertThat(matches(amount = PositiveAmount(99_999L))).isFalse()
        }
    }
}
