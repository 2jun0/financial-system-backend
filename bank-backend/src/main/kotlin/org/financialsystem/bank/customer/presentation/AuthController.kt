package org.financialsystem.bank.customer.presentation

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.financialsystem.bank.customer.application.AuthService
import org.financialsystem.bank.customer.application.dto.RegisterRequest
import org.financialsystem.bank.customer.presentation.dto.LoginRequest
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
    private val sessionAuthenticationStrategy: SessionAuthenticationStrategy,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun register(
        @RequestBody registerRequest: RegisterRequest,
    ) {
        authService.registerCustomer(registerRequest)
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun login(
        @RequestBody loginRequest: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val authenticationToken =
            UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.loginId,
                loginRequest.password,
            )

        val authentication = authenticationManager.authenticate(authenticationToken)
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response)

        val securityContext = SecurityContextHolder.createEmptyContext()
        securityContext.authentication = authentication

        SecurityContextHolder.setContext(securityContext)
        securityContextRepository.saveContext(securityContext, request, response)
    }
}
