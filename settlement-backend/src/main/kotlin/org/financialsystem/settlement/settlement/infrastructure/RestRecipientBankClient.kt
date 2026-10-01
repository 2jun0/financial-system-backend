package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.application.RecipientBankClient
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.ValidateSettlementResponse
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
        restClient
            .post()
            .uri("$bankUri/settlement-validations")
            .body(request)
            .retrieve()
            .body<ValidateSettlementResponse>()
            ?: throw RestClientException("수취 검증 응답이 비어 있습니다.")

    override fun notifySettledResult(
        bankUri: String,
        transferKey: String,
    ) {
        restClient
            .post()
            .uri("$bankUri/settlement-results/{transferKey}/settled", transferKey)
            .retrieve()
            .toBodilessEntity()
    }

    override fun notifyRejectedResult(
        bankUri: String,
        transferKey: String,
    ) {
        restClient
            .post()
            .uri("$bankUri/settlement-results/{transferKey}/rejected", transferKey)
            .retrieve()
            .toBodilessEntity()
    }
}
