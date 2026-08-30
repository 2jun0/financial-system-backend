package org.financialsystem.bank.customer.application.exception

class DuplicateLoginIdException(
    loginId: String,
) : RuntimeException("이미 사용 중인 로그인 아이디입니다: $loginId")
