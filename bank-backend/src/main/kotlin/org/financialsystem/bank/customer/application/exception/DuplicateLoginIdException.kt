package org.financialsystem.bank.customer.application.exception

import org.financialsystem.bank.support.exception.ConflictException

class DuplicateLoginIdException(
    message: String,
) : ConflictException("DUPLICATE_LOGIN_ID", message)
