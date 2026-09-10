package org.financialsystem.settlement.settlement.presentation

import jakarta.validation.Valid
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class SettlementController(
    private val settlementService: SettlementService,
) {
    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun initiate(
        @Valid @RequestBody request: InitiateSettlementRequest,
    ) {
        settlementService.initiate(request)
    }

    @GetMapping("/settlements/pending")
    @ResponseStatus(HttpStatus.OK)
    fun findPendingSettlements(
        @RequestParam("recipientBankCode") recipientBankCode: String,
    ): List<PendingSettlementResponse> = settlementService.findPendingSettlements(recipientBankCode)
}
