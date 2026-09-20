package org.financialsystem.bank.settlement.presentation

import jakarta.validation.Valid
import org.financialsystem.bank.settlement.application.SettlementResultService
import org.financialsystem.bank.settlement.application.SettlementValidationService
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementRequest
import org.financialsystem.bank.settlement.application.dto.ValidateSettlementResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class SettlementController(
    private val settlementResultService: SettlementResultService,
    private val settlementValidationService: SettlementValidationService,
) {
    @PostMapping("/settlement-validations")
    @ResponseStatus(HttpStatus.OK)
    fun validate(
        @Valid @RequestBody request: ValidateSettlementRequest,
    ): ValidateSettlementResponse = settlementValidationService.validateSettlement(request)

    @PostMapping("/settlement-results/{transferKey}/settled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun applySettledResult(
        @PathVariable transferKey: String,
    ) {
        settlementResultService.applySettledResult(transferKey)
    }

    @PostMapping("/settlement-results/{transferKey}/rejected")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun applyRejectedResult(
        @PathVariable transferKey: String,
    ) {
        settlementResultService.applyRejectedResult(transferKey)
    }
}
