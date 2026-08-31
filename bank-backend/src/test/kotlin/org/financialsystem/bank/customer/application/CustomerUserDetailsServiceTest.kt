package org.financialsystem.bank.customer.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.customer.domain.Customer
import org.financialsystem.bank.customer.infrastructure.CustomerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
class CustomerUserDetailsServiceTest {
    @Mock
    lateinit var customerRepository: CustomerRepository

    @Test
    fun `로그인 아이디로 고객을 조회한 후 userDetail으로 변환한다`() {
        val loginId = "Jun0"
        val passwordHash = "passwordHash"
        val customer = Customer.create(loginId, passwordHash)
        ReflectionTestUtils.setField(customer, "id", 1L)
        given(customerRepository.findByLoginId(loginId))
            .willReturn(customer)

        val customerUserDetailsService = CustomerUserDetailsService(customerRepository)
        val result = customerUserDetailsService.loadUserByUsername(loginId)

        assertThat(result.username).isEqualTo(loginId)
        assertThat(result.password).isEqualTo(passwordHash)
    }

    @Test
    fun `로그인 아이디에 해당하는 고객이 없으면 예외를 발생시킨다`() {
        val loginId = "Jun0"
        given(customerRepository.findByLoginId(loginId))
            .willReturn(null)

        val customerUserDetailsService = CustomerUserDetailsService(customerRepository)
        assertThatThrownBy {
            customerUserDetailsService.loadUserByUsername(loginId)
        }.isInstanceOf(UsernameNotFoundException::class.java)
    }
}
