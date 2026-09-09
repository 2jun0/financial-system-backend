package org.financialsystem.settlement.support.exception

abstract class BaseException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

abstract class NotFoundException(
    code: String,
    message: String,
) : BaseException(code, message)

abstract class ConflictException(
    code: String,
    message: String,
) : BaseException(code, message)

abstract class InvalidRequestException(
    code: String,
    message: String,
) : BaseException(code, message)

abstract class UnauthorizedException(
    code: String,
    message: String,
    cause: Throwable? = null,
) : BaseException(code, message, cause)
