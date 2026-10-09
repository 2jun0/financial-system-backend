package org.financialsystem.settlement.settlement.application.exception

class RecipientBankClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
