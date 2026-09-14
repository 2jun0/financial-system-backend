package org.financialsystem.settlement.settlement.presentation

import jakarta.validation.Valid
import org.financialsystem.settlement.settlement.application.SettlementService
import org.financialsystem.settlement.settlement.application.dto.AcceptSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.InitiateSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.PendingSettlementResponse
import org.financialsystem.settlement.settlement.application.dto.RejectSettlementRequest
import org.financialsystem.settlement.settlement.application.dto.SettlementDetailResponse
import org.financialsystem.settlement.settlement.application.dto.SettlementStateResponse
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
    fun initiate(
        @Valid @RequestBody request: InitiateSettlementRequest,
    ): InitiateSettlementResponse = settlementService.initiate(request)

    @GetMapping("/settlements/pending")
    @ResponseStatus(HttpStatus.OK)
    fun findPendingSettlements(
        @RequestParam("recipientBankCode") recipientBankCode: String,
    ): List<PendingSettlementResponse> = settlementService.findPendingSettlements(recipientBankCode)

    @PostMapping("/settlements/{transferKey}/accept")
    @ResponseStatus(HttpStatus.OK)
    fun accept(
        @PathVariable transferKey: String,
        @Valid @RequestBody request: AcceptSettlementRequest,
    ): SettlementStateResponse = settlementService.accept(transferKey, request)

    @PostMapping("/settlements/{transferKey}/reject")
    @ResponseStatus(HttpStatus.OK)
    fun reject(
        @PathVariable transferKey: String,
        @Valid @RequestBody request: RejectSettlementRequest,
    ): SettlementStateResponse = settlementService.reject(transferKey, request)

    @GetMapping("/settlements/{transferKey}")
    @ResponseStatus(HttpStatus.OK)
    fun find(
        @PathVariable transferKey: String,
        @RequestParam("sourceBankCode") sourceBankCode: String,
    ): SettlementDetailResponse = settlementService.find(transferKey, sourceBankCode)
}
