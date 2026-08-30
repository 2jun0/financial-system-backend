package org.financialsystem.bank.customer.presentation.dto

data class LoginRequest(
    val loginId: String,
    val password: String,
) {
    override fun toString(): String = "LoginRequest(loginId=$loginId, password=[REDACTED])"
}
