# Financial System Backend

Kotlin(Java 25), Spring Boot, JPA, Mysql, Flyway  
송금 시스템 백엔드 프로젝트 입니다. 여러 서비스에 걸친 송금 업무와 장애 발생 이후의 복구를 직접 설계하고 구현하는 것을 목표로 합니다.

### 주요 기능

- 회원 및 계좌관리: 회원가입, 로그인, 계좌 생성 및 조회
- 타행 은행 송금: 송금 계좌 출금 부터 은행 간 결제, 수취 계좌 입금 처리
- 송금 복구: Spring 스케줄러로 미완료 거래를 조회해 결제 결과에 따라 복구

### 정상 송금 시나리오

- 자세한 서비스별 시나리오는 [시나리오 문서](docs/scenarios)를 참고하세요.

```mermaid
sequenceDiagram
    autonumber
    actor 고객
    participant A as 은행 A
    participant C as 중앙 결제
    participant B as 은행 B
    고객 ->> A: 송금 요청
    A ->> A: 출금 + 출금 기록 + 송금중 저장
    A ->> C: E-001 결제 요청
    C ->> C: 수취검증대기 저장
    C ->> B: 수취 검증 요청
    B ->> B: 계좌 확인 + 수취중 저장
    B -->> C: 수취 가능
    C ->> C: 은행 결제잔액 이동 + 결제완료 저장
    C ->> B: E-001 결제완료 결과 통지
    B ->> B: 입금 + 입금 기록 + 수취 성공 저장
    C -->> A: 결제완료
    A ->> A: 송금 성공 저장
    A -->> 고객: 송금 성공
```

## AI 사용

AI는 [기여 지침](docs/CONTRIBUTING.md)을 참고하며 다음과 같은 작업을 주로 담당합니다.

- 이슈 작성, 브랜치 및 PR 관리, 커밋 문구 작성
- 단순 반복 코딩, 의사결정 이력 문서 관리
