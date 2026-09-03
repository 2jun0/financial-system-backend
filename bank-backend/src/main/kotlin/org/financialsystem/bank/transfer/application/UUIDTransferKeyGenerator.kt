package org.financialsystem.bank.transfer.application

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UUIDTransferKeyGenerator : TransferKeyGenerator {
    override fun generate(): String = UUID.randomUUID().toString()
}
