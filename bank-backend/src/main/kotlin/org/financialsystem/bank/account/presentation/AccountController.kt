package org.financialsystem.bank.account.presentation

import org.financialsystem.bank.account.application.AccountService
import org.financialsystem.bank.account.application.dto.CreateAccountRequest
import org.financialsystem.bank.account.presentation.dto.CreateAccountResponse
import org.financialsystem.bank.customer.application.CustomerPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/account")
class AccountController(
    private val accountService: AccountService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(
        @AuthenticationPrincipal principal: CustomerPrincipal,
        @RequestBody createAccountRequest: CreateAccountRequest,
    ): CreateAccountResponse {
        val account = accountService.createAccount(principal.customerId, createAccountRequest)
        return CreateAccountResponse.from(account)
    }
}
