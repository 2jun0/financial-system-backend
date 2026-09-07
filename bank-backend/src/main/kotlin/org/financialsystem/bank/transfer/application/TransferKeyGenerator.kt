package org.financialsystem.bank.transfer.application

interface TransferKeyGenerator {
    fun generate(): String
}
