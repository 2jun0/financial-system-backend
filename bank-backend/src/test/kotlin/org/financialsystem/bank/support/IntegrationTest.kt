package org.financialsystem.bank.support

import org.financialsystem.bank.MySqlTestConfig
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@Import(MySqlTestConfig::class, DatabaseCleaner::class)
@ExtendWith(CleanDatabaseExtension::class)
@ActiveProfiles("integration-test")
annotation class IntegrationTest
