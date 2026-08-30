package org.financialsystem.bank.customer.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.financialsystem.bank.customer.application.dto.RegisterRequest
import org.financialsystem.bank.customer.application.exception.DuplicateLoginIdException
import org.financialsystem.bank.customer.domain.Customer
import org.financialsystem.bank.customer.infrastructure.CustomerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {
    @Mock
    lateinit var customerRepository: CustomerRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `회원가입 시 비밀번호를 암호화하여 고객을 저장한다`() {
        val registerRequest = RegisterRequest("loginId", "password$123")
        val passwordHash = "passwordHash$123"
        given(passwordEncoder.encode(registerRequest.password))
            .willReturn(passwordHash)
        given(customerRepository.existsByLoginId(registerRequest.loginId)).willReturn(false)

        val authService = AuthService(customerRepository, passwordEncoder)
        authService.registerCustomer(registerRequest)

        val argumentCaptor = ArgumentCaptor.forClass(Customer::class.java)
        verify(customerRepository).save(argumentCaptor.capture())
        val savedCustomer = argumentCaptor.value

        assertThat(savedCustomer.loginId).isEqualTo(registerRequest.loginId)
        assertThat(savedCustomer.passwordHash).isEqualTo(passwordHash)
    }

    @Test
    fun `중복된 로그인 아이디로 회원가입 시 예외를 던진다`() {
        val loginId = "loginId"
        val password = "password$123"
        val registerRequest = RegisterRequest(loginId, password)
        given(customerRepository.existsByLoginId(loginId)).willReturn(true)

        val authService = AuthService(customerRepository, passwordEncoder)
        assertThatThrownBy {
            authService.registerCustomer(registerRequest)
        }.isInstanceOf(DuplicateLoginIdException::class.java)
    }
}
