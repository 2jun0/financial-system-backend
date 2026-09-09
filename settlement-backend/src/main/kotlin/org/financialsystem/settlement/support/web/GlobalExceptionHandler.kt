package org.financialsystem.settlement.support.web

import org.financialsystem.settlement.support.exception.BaseException
import org.financialsystem.settlement.support.exception.ConflictException
import org.financialsystem.settlement.support.exception.InvalidRequestException
import org.financialsystem.settlement.support.exception.NotFoundException
import org.financialsystem.settlement.support.exception.UnauthorizedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val httpStatusMapper =
        mapOf(
            NotFoundException::class to HttpStatus.NOT_FOUND,
            ConflictException::class to HttpStatus.CONFLICT,
            InvalidRequestException::class to HttpStatus.BAD_REQUEST,
            UnauthorizedException::class to HttpStatus.UNAUTHORIZED,
        )

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(exception: BaseException): ProblemDetail {
        val status =
            httpStatusMapper.entries
                .firstOrNull { (type, _) ->
                    type.isInstance(exception)
                }?.value ?: HttpStatus.INTERNAL_SERVER_ERROR
        val message =
            if (status.is5xxServerError) {
                logger.error("서버 내부 오류, code=${exception.code}", exception)
                "Internal Server Error"
            } else {
                exception.message
            }

        return errorResponse(status, message, exception.code)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(exception: Exception): ProblemDetail {
        logger.error("서버 내부 오류", exception)

        return errorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "INTERNAL_SERVER_ERROR",
        )
    }

    private fun errorResponse(
        status: HttpStatus,
        detail: String,
        code: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty("code", code)
        }
}
