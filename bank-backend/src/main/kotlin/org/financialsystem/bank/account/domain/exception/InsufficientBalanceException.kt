package org.financialsystem.bank.account.domain.exception

import org.financialsystem.bank.support.exception.ConflictException

class InsufficientBalanceException(
    message: String,
) : ConflictException("INSUFFICIENT_BALANCE", message)
