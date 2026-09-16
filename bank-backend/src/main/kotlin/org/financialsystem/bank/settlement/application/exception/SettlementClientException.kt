package org.financialsystem.bank.settlement.application.exception

class SettlementClientException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
