package org.financialsystem.e2e.support

import java.sql.Statement
import javax.sql.DataSource

class DatabaseCleaner(
    private val dataSource: DataSource,
) {
    fun clean() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 0")
                try {
                    val tables = getTables(statement)
                    for (table in tables) {
                        @Suppress("SqlSourceToSinkFlow")
                        statement.execute("TRUNCATE TABLE $table")
                    }
                } finally {
                    statement.execute("SET SESSION FOREIGN_KEY_CHECKS = 1")
                }
            }
        }
    }

    private fun getTables(statement: Statement): List<String> =
        statement
            .executeQuery(
                "SELECT table_name\n" +
                    "FROM information_schema.tables\n" +
                    "WHERE table_schema = DATABASE()\n" +
                    "  AND table_type = 'BASE TABLE'\n" +
                    "  AND table_name <> 'flyway_schema_history'",
            ).use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }
}
