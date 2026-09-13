package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.ForbiddenException

class RecipientBankMismatchException(
    message: String? = null,
) : ForbiddenException("RECIPIENT_BANK_MISMATCH", message)
