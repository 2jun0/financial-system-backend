package org.financialsystem.bank.money.domain

@JvmInline
value class PositiveAmount(
    val value: Long,
) {
    init {
        require(value > 0) { "금액은 0보다 커야 합니다. 입력값: $value" }
    }
}
