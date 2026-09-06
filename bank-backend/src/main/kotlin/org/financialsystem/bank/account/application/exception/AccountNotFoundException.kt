package org.financialsystem.bank.account.application.exception

import org.financialsystem.bank.support.exception.NotFoundException

class AccountNotFoundException(
    message: String,
) : NotFoundException("ACCOUNT_NOT_FOUND", message)
