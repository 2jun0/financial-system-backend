package org.financialsystem.settlement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SettlementBackendApplication

fun main(args: Array<String>) {
    runApplication<SettlementBackendApplication>(*args)
}
