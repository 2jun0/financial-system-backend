package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.application.RecipientBankClient
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementResponse
import org.financialsystem.settlement.settlement.application.exception.RecipientBankClientException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

@Component
class RestRecipientBankClient(
    builder: RestClient.Builder,
) : RecipientBankClient {
    private val restClient = builder.build()

    override fun validateSettlement(
        bankUri: String,
        request: ValidateSettlementRequest,
    ): ValidateSettlementResponse =
        withExceptionHandler {
            restClient
                .post()
                .uri("$bankUri/settlement-validations")
                .body(request)
                .retrieve()
                .body<ValidateSettlementResponse>()
                ?: throw RecipientBankClientException("수취 검증 응답이 비어 있습니다.")
        }

    override fun notifySettledResult(
        bankUri: String,
        transferKey: String,
    ) {
        withExceptionHandler {
            restClient
                .post()
                .uri("$bankUri/settlement-results/{transferKey}/settled", transferKey)
                .retrieve()
                .toBodilessEntity()
        }
    }

    override fun notifyRejectedResult(
        bankUri: String,
        transferKey: String,
    ) {
        withExceptionHandler {
            restClient
                .post()
                .uri("$bankUri/settlement-results/{transferKey}/rejected", transferKey)
                .retrieve()
                .toBodilessEntity()
        }
    }

    private fun <T> withExceptionHandler(action: () -> T): T =
        try {
            action()
        } catch (exception: RestClientException) {
            throw RecipientBankClientException("수취 은행 요청 처리 중 오류가 발생했습니다.", exception)
        }
}
