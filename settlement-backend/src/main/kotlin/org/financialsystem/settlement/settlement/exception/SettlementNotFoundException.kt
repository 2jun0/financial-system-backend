package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.NotFoundException

class SettlementNotFoundException(
    message: String? = null,
) : NotFoundException("SETTLEMENT_NOT_FOUND", message)
