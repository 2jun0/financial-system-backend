package org.financialsystem.bank.customer.application

import org.financialsystem.bank.customer.infrastructure.CustomerRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomerUserDetailsService(
    private val customerRepository: CustomerRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val customer =
            customerRepository.findByLoginId(username)
                ?: throw UsernameNotFoundException("고객을 찾을 수 없습니다: $username")

        return CustomerPrincipal(
            customer.id!!,
            customer.loginId,
            customer.passwordHash,
        )
    }
}
