package org.financialsystem.settlement.settlement.application

import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.SettlementFixtures
import org.financialsystem.settlement.settlement.domain.SettlementState
import org.financialsystem.settlement.settlement.infrastructure.SettlementRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

@ExtendWith(MockitoExtension::class)
class SettlementRecoveryServiceTest {
    @Mock
    lateinit var settlementRepository: SettlementRepository

    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private lateinit var settlementRecoveryService: SettlementRecoveryService

    @BeforeEach
    fun setUp() {
        settlementRecoveryService =
            SettlementRecoveryService(
                clock = Clock.fixed(now, ZoneOffset.UTC),
                settlementRepository = settlementRepository,
            )
    }

    @Nested
    inner class `결제를 복구한다` {
        @Test
        fun `만료된 수취 검증 대기 결제를 모두 거절한다`() {
            // given
            val settlements =
                List(3) { index ->
                    SettlementFixtures.settlement(
                        transferKey = "transfer-$index",
                        sourceBankId = 1L,
                        recipientBankId = 2L,
                        transferCreatedAt = now.minus(SETTLEMENT_TIME_LIMIT).minusSeconds(1),
                        createdAt = now.minusSeconds(10),
                    )
                }
            given(settlementRepository.findRecoveryTargets(any(), any(), any(), any(), any(), any()))
                .willReturn(settlements, emptyList())

            // when
            settlementRecoveryService.recoverUnresolvedSettlements()

            // then
            assertThat(settlements).allSatisfy {
                assertThat(it.state).isEqualTo(SettlementState.REJECTED)
            }
        }
    }
}
