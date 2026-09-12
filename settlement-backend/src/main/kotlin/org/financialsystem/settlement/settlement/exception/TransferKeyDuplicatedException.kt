package org.financialsystem.settlement.settlement.exception

import org.financialsystem.settlement.support.exception.ConflictException

class TransferKeyDuplicatedException(
    message: String? = null,
) : ConflictException("TRANSFER_KEY_DUPLICATED_EXCEPTION", message)
