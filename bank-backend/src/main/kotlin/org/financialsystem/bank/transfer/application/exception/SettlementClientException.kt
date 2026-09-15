package org.financialsystem.bank.transfer.application.exception

class SettlementClientException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
