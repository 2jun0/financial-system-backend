package org.financialsystem.bank.settlement.infrastructure

import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

@Component
class RestSettlementClient(
    builder: RestClient.Builder,
    @Value($$"${settlement.base-url}") baseUrl: String,
) : SettlementClient {
    private val restClient = builder.baseUrl(baseUrl).build()

    override fun initiate(request: InitiateSettlementRequest) {
        try {
            restClient
                .post()
                .uri("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        }
    }
}
