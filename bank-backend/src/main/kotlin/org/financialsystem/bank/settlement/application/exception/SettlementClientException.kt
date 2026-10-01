package org.financialsystem.bank.settlement.application.exception

class SettlementClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
