package org.financialsystem.bank.customer.presentation.exception

import org.financialsystem.bank.support.exception.UnauthorizedException

class InvalidCredentialsException(
    message: String,
    cause: Throwable? = null,
) : UnauthorizedException("INVALID_CREDENTIALS", message, cause)
