package org.financialsystem.settlement.bank.exception

import org.financialsystem.settlement.support.exception.NotFoundException

class BankNotFoundException(
    message: String,
) : NotFoundException(
        "BANK_NOT_FOUND",
        message,
    )
