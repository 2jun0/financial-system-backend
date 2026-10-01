package org.financialsystem.bank.settlement.infrastructure

import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.SettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementResponse
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class RestSettlementClient(
    builder: RestClient.Builder,
    @Value($$"${settlement.base-url}") baseUrl: String,
) : SettlementClient {
    private val restClient = builder.baseUrl(baseUrl).build()

    override fun requestSettlement(request: SettlementRequest): SettlementResponse =
        try {
            restClient
                .post()
                .uri("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SettlementResponse::class.java)
                ?: throw SettlementClientException("중앙 결제 서버의 응답이 비어 있습니다.")
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        } catch (exception: RestClientException) {
            throw SettlementClientException("중앙 결제 요청 처리 중 오류가 발생했습니다.", exception)
        }
}
