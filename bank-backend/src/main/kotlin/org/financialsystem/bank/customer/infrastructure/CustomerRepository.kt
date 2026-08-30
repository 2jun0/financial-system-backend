package org.financialsystem.bank.customer.infrastructure

import org.financialsystem.bank.customer.domain.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByLoginId(loginId: String): Customer?

    fun existsByLoginId(loginId: String): Boolean
}
