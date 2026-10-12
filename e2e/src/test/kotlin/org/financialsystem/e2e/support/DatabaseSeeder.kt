package org.financialsystem.e2e.support

import javax.sql.DataSource

class DatabaseSeeder(
    private val dataSource: DataSource,
    private val databaseType: DatabaseType,
) {
    fun seed(name: String) {
        execute("seed/${databaseType.label}/$name.sql")
    }

    private fun execute(path: String) {
        val resource = requireNotNull(javaClass.classLoader.getResource(path)) {
            "시드 파일을 찾을 수 없습니다: $path"
        }
        val sql = resource.readText()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                sql
                    .lineSequence()
                    .filterNot { it.trimStart().startsWith("--") }
                    .joinToString("\n")
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach {
                        @Suppress("SqlSourceToSinkFlow")
                        statement.execute(it)
                    }
            }
        }
    }
}
