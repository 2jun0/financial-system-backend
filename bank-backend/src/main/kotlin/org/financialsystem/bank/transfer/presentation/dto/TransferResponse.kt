package org.financialsystem.bank.transfer.presentation.dto

import org.financialsystem.bank.transfer.domain.Transfer
import org.financialsystem.bank.transfer.domain.TransferState

data class TransferResponse(
    val transferKey: String,
    val state: TransferState,
) {
    companion object {
        fun from(transfer: Transfer) =
            TransferResponse(
                transferKey = transfer.transferKey,
                state = transfer.state,
            )
    }
}
