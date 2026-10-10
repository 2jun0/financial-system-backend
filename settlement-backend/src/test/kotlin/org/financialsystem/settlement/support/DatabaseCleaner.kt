package org.financialsystem.settlement.support

import javax.sql.DataSource

class DatabaseCleaner(
    private val dataSource: DataSource,
) {
    fun clean() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 0")
                try {
                    for (table in listOf("settlements", "banks")) {
                        statement.execute("TRUNCATE TABLE $table")
                    }
                } finally {
                    statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 1")
                }
            }
        }
    }
}
