package org.financialsystem.bank.customer.application

import org.financialsystem.bank.customer.application.dto.RegisterRequest
import org.financialsystem.bank.customer.application.exception.DuplicateLoginIdException
import org.financialsystem.bank.customer.domain.Customer
import org.financialsystem.bank.customer.infrastructure.CustomerRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val customerRepository: CustomerRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun registerCustomer(registerRequest: RegisterRequest) {
        if (customerRepository.existsByLoginId(registerRequest.loginId)) {
            throw DuplicateLoginIdException(registerRequest.loginId)
        }

        val customer =
            Customer.create(
                registerRequest.loginId,
                passwordEncoder.encode(registerRequest.password)!!,
            )
        customerRepository.save(customer)
    }
}
