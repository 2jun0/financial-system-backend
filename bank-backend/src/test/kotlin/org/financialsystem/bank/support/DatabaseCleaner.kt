package org.financialsystem.bank.support

import javax.sql.DataSource

val TABLES = listOf("account_transactions", "transfers", "receipts", "accounts", "customers")

class DatabaseCleaner(
    private val dataSource: DataSource,
) {
    fun clean() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 0")
                try {
                    for (table in TABLES) {
                        statement.execute("TRUNCATE TABLE $table")
                    }
                } finally {
                    statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 1")
                }
            }
        }
    }
}
