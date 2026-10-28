package org.financialsystem.bank.account

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql

@SpringBootTest
@IntegrationTest
@Sql("/sql/balance/initial-balance.sql")
class AccountBalanceConstraintIntegrationTest {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `음수 잔액으로 생성할 수 없다`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO accounts (account_number, customer_id, name, balance, created_at, updated_at)
                VALUES ('CHECK-002', 1, '새 계좌', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
                """.trimIndent(),
                -1L,
            )
        }.isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("ck_accounts_balance_nonnegative")
    }

    @Test
    fun `잔액을 음수로 변경할 수 없다`() {
        assertThatThrownBy {
            jdbcTemplate.update("UPDATE accounts SET balance = -1 WHERE id = 1")
        }.isInstanceOf(DataAccessException::class.java)
            .hasMessageContaining("ck_accounts_balance_nonnegative")
        assertThat(jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = 1", Long::class.java)).isEqualTo(100L)
    }

    @Test
    fun `잔액 0으로 생성하거나 변경할 수 있다`() {
        jdbcTemplate.update(
            """
            INSERT INTO accounts (account_number, customer_id, name, balance, created_at, updated_at)
            VALUES ('CHECK-002', 1, '새 계좌', ?, UTC_TIMESTAMP(), UTC_TIMESTAMP())
            """.trimIndent(),
            0L,
        )
        jdbcTemplate.update("UPDATE accounts SET balance = 0 WHERE id = 1")

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts WHERE balance = 0", Long::class.java)).isEqualTo(2L)
    }
}
