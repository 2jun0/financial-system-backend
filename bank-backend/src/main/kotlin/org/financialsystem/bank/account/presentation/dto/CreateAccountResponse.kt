package org.financialsystem.bank.account.presentation.dto

import org.financialsystem.bank.account.domain.Account

data class CreateAccountResponse(
    val accountNumber: String,
    val name: String,
) {
    companion object {
        fun from(account: Account): CreateAccountResponse =
            CreateAccountResponse(
                account.accountNumber,
                account.name,
            )
    }
}
