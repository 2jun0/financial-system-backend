package org.financialsystem.settlement.settlement.infrastructure

import org.financialsystem.settlement.settlement.domain.Settlement
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long>
