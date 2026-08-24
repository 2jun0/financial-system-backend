package org.financialsystem.bank

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.domain.support.AuditingEntityListener

@Entity
@Table(name = "account_transactions")
@EntityListeners(AuditingEntityListener::class)
class AccountTransaction private constructor(accountId: Long, transferKey: String, otherBankCode: String, amount: Int.) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "account_id", nullable = false)
    var accountId: Long? = null
    protected set
}
