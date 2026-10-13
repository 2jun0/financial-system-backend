package org.financialsystem.e2e.support

import com.mysql.cj.jdbc.MysqlDataSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(
    ContainersStartupExtension::class,
)
abstract class E2ETest {
    private val dataSources =
        mapOf(
            DatabaseType.BANK_A to dataSource(3307, "bank_a", "bank_a_dev_password"),
            DatabaseType.BANK_B to dataSource(3308, "bank_b", "bank_b_dev_password"),
            DatabaseType.SETTLEMENT to dataSource(3309, "settlement", "settlement_dev_password"),
        )

    private val databaseCleaners = dataSources.map { it.key to DatabaseCleaner(it.value) }.toMap()
    private val databaseSeeders = dataSources.map { it.key to DatabaseSeeder(it.value, it.key) }.toMap()

    @BeforeEach
    fun cleanDatabase() {
        databaseCleaners.values.forEach { it.clean() }
    }

    protected fun seed(name: String, vararg types: DatabaseType) {
        val targets = if (types.isEmpty()) databaseSeeders.keys else types.toList()
        targets.forEach { databaseSeeders.getValue(it).seed(name) }
    }

    protected fun dataSource(type: DatabaseType) = dataSources.getValue(type)

    private fun dataSource(
        port: Int,
        database: String,
        password: String,
    ): DataSource =
        MysqlDataSource().apply {
            setURL("jdbc:mysql://127.0.0.1:$port/$database")
            user = database
            setPassword(password)
        }

    companion object {
        const val BANK_A_BASE_URL = "http://localhost:8081"
        const val BANK_B_BASE_URL = "http://localhost:8082"
        const val SETTLEMENT_BASE_URL = "http://localhost:8083"
    }
}
