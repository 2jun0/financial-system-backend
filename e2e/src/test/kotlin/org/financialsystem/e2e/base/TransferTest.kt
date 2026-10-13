package org.financialsystem.e2e.base

import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Extract
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.assertj.core.api.Assertions.assertThat
import org.financialsystem.e2e.support.DatabaseQuerySupport
import org.financialsystem.e2e.support.DatabaseType
import org.financialsystem.e2e.support.E2ETest
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test

class TransferTest : E2ETest() {
    @Test
    fun `송금 계좌의 잔액이 부족하면 송금을 거절한다`() {
        // given
        seed("base")
        val loginCookies = login()

        // when
        Given {
            baseUri(BANK_A_BASE_URL)
            contentType(ContentType.JSON)
            cookies(loginCookies)
            body(
                mapOf(
                    "recipientBankCode" to "BANK_B",
                    "recipientAccountNumber" to "B-001",
                    "amount" to 200_000,
                ),
            )
        } When {
            log().all()
            post("/account/A-001/transfer")
        } Then {
            log().all()
            statusCode(409)
            body("code", equalTo("INSUFFICIENT_BALANCE"))
        }

        // then bank a
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT balance FROM accounts WHERE account_number = 'A-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT * FROM transfers",
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT * FROM account_transactions",
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        // then bank b
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT balance FROM accounts WHERE account_number = 'B-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM receipts",
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM account_transactions",
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        // then settlement
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT * FROM settlements",
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_A'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_000_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_B'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_000_000L)
                assertThat(resultSet.next()).isFalse()
            }
    }

    @Test
    fun `수취 계좌가 없으면 송금을 실패 처리하고 출금을 보상한다`() {
        // given
        seed("base")
        val loginCookies = login()

        // when
        val transferKey =
            Given {
                baseUri(BANK_A_BASE_URL)
                contentType(ContentType.JSON)
                cookies(loginCookies)
                body(
                    mapOf(
                        "recipientBankCode" to "BANK_B",
                        "recipientAccountNumber" to "B-404",
                        "amount" to 10_000,
                    ),
                )
            } When {
                log().all()
                post("/account/A-001/transfer")
            } Then {
                log().all()
                statusCode(200)
                body("state", equalTo("FAILED"))
            } Extract {
                path<String>("transferKey")
            }

        // then bank a
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT balance FROM accounts WHERE account_number = 'A-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT state FROM transfers WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("state")).isEqualTo("FAILED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT type, amount FROM account_transactions WHERE transfer_key = ? ORDER BY created_at ASC, id ASC",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("type")).isEqualTo("DEBIT")
                assertThat(resultSet.getLong("amount")).isEqualTo(-10_000L)
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("type")).isEqualTo("COMPENSATION")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000L)
                assertThat(resultSet.next()).isFalse()
            }

        // then bank b
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT balance FROM accounts WHERE account_number = 'B-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM receipts WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM account_transactions WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        // then settlement
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT state FROM settlements WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("state")).isEqualTo("REJECTED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_A'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_000_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_B'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_000_000L)
                assertThat(resultSet.next()).isFalse()
            }
    }

    @Test
    fun `송금 은행 잔액이 부족하면 송금을 실패 처리하고 출금을 보상한다`() {
        // given
        seed("base", DatabaseType.BANK_A, DatabaseType.BANK_B)
        seed("insufficient-bank-a-balance", DatabaseType.SETTLEMENT)
        val loginCookies = login()

        // when
        val transferKey =
            Given {
                baseUri(BANK_A_BASE_URL)
                contentType(ContentType.JSON)
                cookies(loginCookies)
                body(
                    mapOf(
                        "recipientBankCode" to "BANK_B",
                        "recipientAccountNumber" to "B-001",
                        "amount" to 10_000,
                    ),
                )
            } When {
                log().all()
                post("/account/A-001/transfer")
            } Then {
                log().all()
                statusCode(200)
                body("state", equalTo("FAILED"))
            } Extract {
                path<String>("transferKey")
            }

        // then bank a
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT balance FROM accounts WHERE account_number = 'A-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT state FROM transfers WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("state")).isEqualTo("FAILED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT type, amount FROM account_transactions WHERE transfer_key = ? ORDER BY created_at ASC, id ASC",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("type")).isEqualTo("DEBIT")
                assertThat(resultSet.getLong("amount")).isEqualTo(-10_000L)
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("type")).isEqualTo("COMPENSATION")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000L)
                assertThat(resultSet.next()).isFalse()
            }

        // then bank b
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT balance FROM accounts WHERE account_number = 'B-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(100_000)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM account_transactions WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT state FROM receipts WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("state")).isEqualTo("FAILED")
                assertThat(resultSet.next()).isFalse()
            }

        // then settlement
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT state FROM settlements WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getString("state")).isEqualTo("REJECTED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_A'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(9_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_B'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_000_000L)
                assertThat(resultSet.next()).isFalse()
            }
    }

    @Test
    fun `송금 성공`() {
        // given
        seed("base")
        val loginCookies = login()

        // when
        val transferKey =
            Given {
                baseUri(BANK_A_BASE_URL)
                contentType(ContentType.JSON)
                cookies(loginCookies)
                body(
                    mapOf(
                        "recipientBankCode" to "BANK_B",
                        "recipientAccountNumber" to "B-001",
                        "amount" to 10_000,
                    ),
                )
            } When {
                log().all()
                post("/account/A-001/transfer")
            } Then {
                log().all()
                statusCode(200)
                body("state", equalTo("SUCCEEDED"))
            } Extract {
                path<String>("transferKey")
            }

        // then bank a
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT balance FROM accounts WHERE account_number='A-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(90_000)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT * FROM transfers WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("source_account_id")).isEqualTo(1L)
                assertThat(resultSet.getString("recipient_bank_code")).isEqualTo("BANK_B")
                assertThat(resultSet.getString("recipient_account_number")).isEqualTo("B-001")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000)
                assertThat(resultSet.getString("state")).isEqualTo("SUCCEEDED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_A),
                sql = "SELECT * FROM account_transactions WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("account_id")).isEqualTo(1L)
                assertThat(resultSet.getString("other_bank_code")).isEqualTo("BANK_B")
                assertThat(resultSet.getString("other_account_number")).isEqualTo("B-001")
                assertThat(resultSet.getLong("amount")).isEqualTo(-10_000)
                assertThat(resultSet.getString("type")).isEqualTo("DEBIT")
                assertThat(resultSet.next()).isFalse()
            }

        // then bank b
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT balance FROM accounts WHERE account_number='B-001'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(110_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM receipts WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("recipient_account_id")).isEqualTo(1L)
                assertThat(resultSet.getString("source_bank_code")).isEqualTo("BANK_A")
                assertThat(resultSet.getString("source_account_number")).isEqualTo("A-001")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000)
                assertThat(resultSet.getString("state")).isEqualTo("SUCCEEDED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.BANK_B),
                sql = "SELECT * FROM account_transactions WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("account_id")).isEqualTo(1L)
                assertThat(resultSet.getString("other_bank_code")).isEqualTo("BANK_A")
                assertThat(resultSet.getString("other_account_number")).isEqualTo("A-001")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000)
                assertThat(resultSet.getString("type")).isEqualTo("CREDIT")
                assertThat(resultSet.next()).isFalse()
            }

        // then settlement
        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT * FROM settlements WHERE transfer_key = ?",
                transferKey,
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("source_bank_id")).isEqualTo(1L)
                assertThat(resultSet.getString("source_account_number")).isEqualTo("A-001")
                assertThat(resultSet.getLong("recipient_bank_id")).isEqualTo(2L)
                assertThat(resultSet.getString("recipient_account_number")).isEqualTo("B-001")
                assertThat(resultSet.getLong("amount")).isEqualTo(10_000)
                assertThat(resultSet.getString("state")).isEqualTo("SETTLED")
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_A'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(990_000L)
                assertThat(resultSet.next()).isFalse()
            }

        DatabaseQuerySupport
            .query(
                dataSource = dataSource(DatabaseType.SETTLEMENT),
                sql = "SELECT balance FROM banks WHERE bank_code = 'BANK_B'",
            ) { resultSet ->
                assertThat(resultSet.next()).isTrue()
                assertThat(resultSet.getLong("balance")).isEqualTo(1_010_000L)
                assertThat(resultSet.next()).isFalse()
            }
    }

    private fun login(): Map<String, String> =
        Given {
            baseUri(BANK_A_BASE_URL)
            contentType(ContentType.JSON)
            body(
                mapOf(
                    "loginId" to "customer-a",
                    "password" to "1234",
                ),
            )
        } When {
            log().all()
            post("/auth/login")
        } Then {
            log().all()
            statusCode(204)
        } Extract {
            cookies()
        }
}
