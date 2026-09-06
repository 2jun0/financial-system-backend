package org.financialsystem.bank.transfer.application.exception

import org.financialsystem.bank.support.exception.InvalidRequestException

class SameBankTransferNotSupportException(
    message: String,
) : InvalidRequestException("SAME_BANK_TRANSFER_NOT_SUPPORTED", message)
