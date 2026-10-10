package org.financialsystem.settlement.support

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.beans.factory.getBean
import org.springframework.test.context.junit.jupiter.SpringExtension

class CleanDatabaseExtension : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) {
        SpringExtension
            .getApplicationContext(context)
            .getBean<DatabaseCleaner>()
            .clean()
    }
}
