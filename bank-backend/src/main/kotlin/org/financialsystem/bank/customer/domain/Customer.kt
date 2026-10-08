package org.financialsystem.bank.customer.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.financialsystem.bank.support.persistence.IdCreatedAtEntity
import org.springframework.data.annotation.LastModifiedDate
import java.time.Instant

@Entity
@Table(
    name = "customers",
    uniqueConstraints = [
        UniqueConstraint(name = "uc_customers_login", columnNames = ["login_id"]),
    ],
)
class Customer private constructor(
    loginId: String,
    passwordHash: String,
) : IdCreatedAtEntity() {
    @Column(name = "login_id", nullable = false)
    var loginId: String = loginId
        protected set

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = passwordHash
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    companion object {
        fun create(
            loginId: String,
            passwordHash: String,
        ): Customer = Customer(loginId, passwordHash)
    }
}
