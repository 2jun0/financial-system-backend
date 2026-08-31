package org.financialsystem.bank.customer.application

import org.springframework.security.core.userdetails.User

class CustomerPrincipal(
    val customerId: Long,
    loginId: String,
    passwordHash: String,
) : User(loginId, passwordHash, emptyList())
