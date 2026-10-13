package org.financialsystem.e2e.support

import java.sql.ResultSet
import javax.sql.DataSource

class DatabaseQuerySupport {
    companion object {
        fun query(
            dataSource: DataSource,
            sql: String,
            vararg params: Any?,
            callback: (ResultSet) -> Unit,
        ) = dataSource.connection.use { connection ->
            connection.prepareStatement(@Suppress("SqlSourceToSinkFlow") sql).use { statement ->
                params.forEachIndexed { index, value ->
                    statement.setObject(index + 1, value)
                }

                statement.executeQuery().use { resultSet ->
                    callback(resultSet)
                }
            }
        }
    }
}
