package org.financialsystem.settlement.settlement.presentation

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.BatchGetSettlementStatesResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    @ResponseStatus(HttpStatus.OK)
    fun requestSettlement(
        @Valid @RequestBody request: SettlementRequest,
    ): SettlementResponse = settlementService.requestSettlement(request)

    @GetMapping("/settlements/{transferKey}")
    @ResponseStatus(HttpStatus.OK)
    fun find(
        @PathVariable transferKey: String,
        @RequestParam("sourceBankCode") sourceBankCode: String,
    ): SettlementDetailResponse = settlementService.find(transferKey, sourceBankCode)

    @GetMapping("/settlements:batchGetStates")
    @ResponseStatus(HttpStatus.OK)
    fun batchGetSettlementStates(
        @RequestParam("bankCode") bankCode: String,
        @RequestParam("transferKeys") @Size(min = 1, max = 100)
        transferKeys: List<String>,
    ): BatchGetSettlementStatesResponse = settlementService.batchGetSettlementStates(bankCode, transferKeys)
}
