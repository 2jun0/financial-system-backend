package org.financialsystem.bank.customer.infrastructure

import org.financialsystem.bank.customer.domain.Customer
import org.springframework.data.jpa.repository.JpaRepository

interface CustomerRepository : JpaRepository<Customer, Long>
