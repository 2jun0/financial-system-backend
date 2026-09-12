package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.InvalidRequestException

class SameBankSettlementException(
    message: String,
) : InvalidRequestException("SAME_BANK_SETTLEMENT", message)
