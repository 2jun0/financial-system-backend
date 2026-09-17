package org.financialsystem.bank.settlement.infrastructure

import org.financialsystem.bank.settlement.application.SettlementClient
import org.financialsystem.bank.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.bank.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.bank.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.bank.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.bank.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.bank.settlement.application.dto.SettlementStateResponse
import org.financialsystem.bank.settlement.application.exception.SettlementClientException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

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

    override fun findPendingSettlements(recipientBankCode: String): List<PendingSettlementResponse> {
        try {
            return restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/settlements/pending")
                        .queryParam("recipientBankCode", recipientBankCode)
                        .build()
                }.retrieve()
                .body<List<PendingSettlementResponse>>() ?: error("중앙 결제 조회 응답 본문이 없습니다.")
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        }
    }

    override fun reject(
        transferKey: String,
        request: RejectSettlementRequest,
    ): SettlementStateResponse {
        try {
            return restClient
                .post()
                .uri("/settlements/$transferKey/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body<SettlementStateResponse>() ?: error("중앙 결제 거절 응답 본문이 없습니다.")
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        }
    }

    override fun accept(
        transferKey: String,
        request: AcceptSettlementRequest,
    ): SettlementStateResponse {
        try {
            return restClient
                .post()
                .uri("/settlements/$transferKey/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body<SettlementStateResponse>() ?: error("중앙 결제 승인 응답 본문이 없습니다.")
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        }
    }

    override fun find(transferKey: String): SettlementDetailResponse {
        try {
            return restClient
                .get()
                .uri("/settlements/$transferKey")
                .retrieve()
                .body<SettlementDetailResponse>() ?: error("중앙 결제 상세 응답 본문이 없습니다.")
        } catch (exception: ResourceAccessException) {
            throw SettlementClientException("중앙 결제 서버에 연결할 수 없습니다.", exception)
        } catch (exception: HttpClientErrorException) {
            throw SettlementClientException("중앙 결제 서버가 요청을 거절했습니다. status=${exception.statusCode}", exception)
        } catch (exception: HttpServerErrorException) {
            throw SettlementClientException("중앙 결제 서버 오류입니다. status=${exception.statusCode}", exception)
        }
    }
}
