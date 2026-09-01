package org.financialsystem.bank.account.presentation.dto

import org.financialsystem.bank.account.domain.Account

data class AccountResponse(
    val id: Long,
    val name: String,
    val accountNumber: String,
    val balance: Long,
) {
    companion object {
        fun from(account: Account): AccountResponse = AccountResponse(account.id!!, account.name, account.accountNumber, account.balance)
    }
}
