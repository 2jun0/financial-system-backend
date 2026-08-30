package org.financialsystem.bank.customer.application.dto

data class RegisterRequest(
    val loginId: String,
    val password: String,
) {
    override fun toString(): String = "RegisterRequest(loginId=$loginId, password=[REDACTED])"
}
