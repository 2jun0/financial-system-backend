package org.financialsystem.bank.config

import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.time.Duration

private val READ_TIMEOUT = Duration.ofSeconds(30)

@Configuration(proxyBeanMethods = false)
class RestClientConfig {
    @Bean
    fun restClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder ->
            builder.requestFactory(
                JdkClientHttpRequestFactory().apply {
                    setReadTimeout(READ_TIMEOUT)
                },
            )
        }
}
