package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.ConflictException

class SameBankSettlementException(
    message: String,
) : ConflictException("SAME_BANK_SETTLEMENT", message)
