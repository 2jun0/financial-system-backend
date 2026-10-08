package org.financialsystem.settlement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class SettlementBackendApplication

fun main(args: Array<String>) {
    runApplication<SettlementBackendApplication>(*args)
}
