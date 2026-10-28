package org.financialsystem.settlement.bank

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.settlement.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql

@SpringBootTest
@IntegrationTest
@Sql("/sql/balance/initial-balance.sql")
class BankBalanceConstraintIntegrationTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `음수 잔액으로 생성할 수 없다`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO banks (bank_code, base_uri, balance, created_at, updated_at)
                VALUES ('BANK_B', 'http://unused', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
                """.trimIndent(),
                -1L,
            )
        }.isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("ck_banks_balance_nonnegative")
    }

    @Test
    fun `잔액을 음수로 변경할 수 없다`() {
        assertThatThrownBy {
            jdbcTemplate.update("UPDATE banks SET balance = -1 WHERE id = 1")
        }.isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("ck_banks_balance_nonnegative")
        assertThat(jdbcTemplate.queryForObject("SELECT balance FROM banks WHERE id = 1", Long::class.java)).isEqualTo(100L)
    }

    @Test
    fun `잔액 0으로 생성하거나 변경할 수 있다`() {
        jdbcTemplate.update(
            """
            INSERT INTO banks (bank_code, base_uri, balance, created_at, updated_at)
            VALUES ('BANK_B', 'http://unused', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
            """.trimIndent(),
            0L,
        )
        jdbcTemplate.update("UPDATE banks SET balance = 0 WHERE id = 1")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM banks WHERE balance = 0", Long::class.java)).isEqualTo(2L)
    }
}
