package org.financialsystem.bank.transfer.presentation

import jakarta.validation.Valid
import org.financialsystem.bank.customer.application.CustomerPrincipal
import org.financialsystem.bank.transfer.application.TransferService
import org.financialsystem.bank.transfer.application.dto.TransferRequest
import org.financialsystem.bank.transfer.presentation.dto.TransferResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/account/{accountNumber}")
class TransferController(
    val transferService: TransferService,
) {
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.OK)
    fun transfer(
        @AuthenticationPrincipal principal: CustomerPrincipal,
        @PathVariable accountNumber: String,
        @Valid @RequestBody transferRequest: TransferRequest,
    ): TransferResponse = transferService.transfer(principal.customerId, accountNumber, transferRequest)
}
