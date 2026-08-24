package org.financialsystem.bank

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "accounts")
class Account private constructor(accountNumber: String, customerId: Long, name: String) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "account_number", nullable = false)
    var accountNumber = accountNumber
        protected set

    @Column(name = "customer_id", nullable = false)
    var customerId: Long? = customerId
        protected set

    @Column(name = "balance", nullable = false)
    var balance: Long = 0
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set

    companion object {
        fun create(accountNumber: String, customerId: Long, name: String): Account {
            return Account(accountNumber, customerId, name)
        }
    }
}
