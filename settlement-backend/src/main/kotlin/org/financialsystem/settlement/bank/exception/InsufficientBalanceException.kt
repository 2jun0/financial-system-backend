package org.financialsystem.settlement.bank.exception

import org.financialsystem.settlement.support.exception.ConflictException

class InsufficientBalanceException(
    message: String,
) : ConflictException("INSUFFICIENT_BALANCE", message)
