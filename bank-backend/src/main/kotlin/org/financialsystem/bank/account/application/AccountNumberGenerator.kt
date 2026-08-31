package org.financialsystem.bank.account.application

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AccountNumberGenerator {
    fun generate(): String = UUID.randomUUID().toString()
}
