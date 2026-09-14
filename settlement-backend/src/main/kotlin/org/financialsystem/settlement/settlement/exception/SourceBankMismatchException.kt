package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.ForbiddenException

class SourceBankMismatchException(
    message: String? = null,
) : ForbiddenException("SOURCE_BANK_MISMATCH", message)
