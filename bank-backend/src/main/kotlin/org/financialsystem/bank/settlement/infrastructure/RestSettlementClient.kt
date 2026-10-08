package org.financialsystem.bank.settlement.infrastructure

import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesRequest
import org.financialsystem.bank.settlement.application.dto.BatchGetSettlementStatesResponse
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
import org.springframework.web.client.body

const val BATCH_GET_SETTLEMENT_STATES_MAX_SIZE = 100

@Component
class RestSettlementClient(
    builder: RestClient.Builder,
    @Value($$"${settlement.base-url}") baseUrl: String,
) : SettlementClient {
    private val restClient = builder.baseUrl(baseUrl).build()

    override fun requestSettlement(request: SettlementRequest): SettlementResponse =
        withExceptionHandler {
            restClient
                .post()
                .uri("/settlements")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body<SettlementResponse>()
                ?: throw SettlementClientException("중앙 결제 서버의 응답이 비어 있습니다.")
        }

    override fun batchGetSettlementStates(request: BatchGetSettlementStatesRequest): BatchGetSettlementStatesResponse =
        withExceptionHandler {
            restClient
                .get()
                .uri {
                    it
                        .path("/settlements:batchGetStates")
                        .queryParam("bankCode", request.bankCode)
                        .queryParam("transferKeys", request.transferKeys as Collection<*>?)
                        .build()
                }.retrieve()
                .body<BatchGetSettlementStatesResponse>()
                ?: throw SettlementClientException("중앙 결제 서버의 응답이 비어 있습니다.")
        }

    private fun <T> withExceptionHandler(action: () -> T): T {
        try {
            return action()
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
}
