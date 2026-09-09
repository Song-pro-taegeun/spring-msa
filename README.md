# Spring MSA Commerce

Spring Boot와 Kafka, Redis, MariaDB를 사용해 멀티테넌트 커머스의 회원,상품,주문 흐름을 구현한 멀티모듈 프로젝트다.

단순히 서비스를 나눈 예제가 아니라 다음 주제를 코드로 실험한다.

- 테넌트별 DB 스키마와 계정을 동적으로 생성하는 Database-per-Tenant 구조
- Transactional Outbox, 수동 ACK, 재시도, DLQ를 이용한 이벤트 신뢰성
- Inbox와 버전 비교를 이용한 상품 스냅샷의 멱등 처리
- Redis Lua를 이용한 원자적 재고 선점과 보상 처리
- Redis Stream을 이용한 주문 접수와 DB 저장의 비동기 분리
- k6를 이용한 동기 주문과 Redis Stream 주문의 부하 테스트 비교


## 모듈 구성

| 모듈 | 포트 / Context path | 저장소 | 역할 |
|---|---|---|---|
| `common-lib` | 서버 없음 | - | 공통 이벤트, DTO, 상태 enum, DB 자격 증명 암,복호화 |
| `auth-service` | `8081` / `/api/auth-service` | `msa_auth` | 회원가입, 로그인, JWT 발급, 테넌트 및 서비스별 DB 자격 증명 생성, 프로비저닝 Outbox 발행 |
| `user-service` | `8082` / `/api/user-service` | `msa_user`, `msa_user_{tenantKey}` | 프로비저닝 이벤트 소비, 사용자용 테넌트 스키마 생성, 테넌트 사용자 조회, DLQ 저장,재처리 |
| `product-service` | `8083` / `/api/product-service` | `msa_product`, Redis | 상품,옵션,재고 생성, Redis 재고 초기화, Order용 상품 스냅샷 Outbox 발행 |
| `order-service` | `8084` / `/api/order-service` | `msa_order`, `msa_order_{tenantKey}`, Redis | 상품 스냅샷 복제, Redis 재고 선점, 주문 저장, Redis Stream 비동기 주문 처리, DLQ 저장,재처리 |

`user-service`와 `order-service`는 요청의 `X-Tenant-Id`를 `TenantContext`에 저장하고, Hibernate `MultiTenantConnectionProvider`가 해당 테넌트의 DataSource를 선택한다. 테넌트 DataSource는 HikariCP로 생성되고 Caffeine으로 캐시된다.

## 핵심 처리 흐름

### 1. 회원가입과 테넌트 프로비저닝

1. `auth-service`가 테넌트와 사용자를 생성한다.
2. `msa_user`, `msa_order` 각각에 사용할 DB 계정과 무작위 비밀번호를 생성한다.
3. 비밀번호는 AES로 암호화하고 비즈니스 데이터와 `tenant-provision` Outbox 이벤트를 같은 트랜잭션에 저장한다.
4. 스케줄러가 Outbox를 조회해 Kafka에 발행한다.
5. `user-service`와 `order-service`가 자기 서비스의 이벤트만 소비한다.
6. 각 서비스가 테넌트 DB 사용자,스키마,권한을 만들고 Flyway migration을 수행한 뒤 최초 사용자를 저장한다.

프로비저닝은 DDL/DCL과 Flyway를 포함하므로 하나의 DB 트랜잭션으로 묶을 수 없다. 대신 `tenant_provision_status`에 마지막 성공 단계를 기록해 재실행 시 완료된 단계를 건너뛴다.

### 2. 상품 등록과 스냅샷 전파

1. `product-service`가 상품, 옵션, 재고를 MariaDB에 저장한다.
2. DB commit 이후 Lua 스크립트가 Redis의 `shared:product-service:inventory:product-option:{optionId}` Hash를 초기화한다.
3. Redis에는 수량뿐 아니라 상품,옵션 ID, 가격, 통화, `updateVersion`을 함께 저장한다.
4. `product-snapshot` Outbox 이벤트가 Kafka로 발행된다.
5. `order-service`가 master schema의 `order_product_snapshot`에 최신 버전만 반영하고, `inbox_event`로 중복 이벤트를 차단한다.

### 3. 주문 처리

두 가지 주문 경로를 비교할 수 있다.

| 경로 | HTTP 응답 | 처리 방식 |
|---|---|---|
| `/order/purchaseProduct` | DB 저장 완료 후 `200` | Redis Lua 재고 선점 → 테넌트 DB 주문 저장 → 실패 시 버전이 같은 경우 Redis 재고 보상 |
| `/order/purchaseProduct/redisOnly` | Redis 접수 후 `202` | Lua 한 번으로 재고 차감,사용자별 원장 기록,Stream `XADD`,이벤트 멱등성 기록 → Consumer가 테넌트 DB에 비동기 저장 → commit 후 `XACK` |

Redis Stream 주문은 `eventId`의 DB unique constraint와 사전 존재 확인으로 재전달 시 중복 저장을 막는다. 기본 Consumer Group은 `order-db-writer`, Stream key는 `order:accepted:stream`, Consumer 수는 16개다.

## 이벤트 신뢰성

### Producer: Transactional Outbox

- 실제 비즈니스 이벤트 기록: `auth-service`, `product-service`
- 상태: `PENDING → PROCESSING → PUBLISHED` 또는 `FAILED`
- 발행 주기: 기본 1초
- 조회 시 row lock과 `FOR UPDATE SKIP LOCKED`로 중복 선점을 방지
- 발행 실패 시 지수 백오프, 기본 최대 5회 시도
- `FAILED` 이벤트는 관리자 API로 다시 `PENDING` 전환 가능

### Consumer: Retry와 DLQ

- Kafka offset은 정상 처리 후 수동 ACK
- 처리 실패 시 1초 간격으로 1회 재시도
- 최종 실패 메시지는 `{service-name}.{original-topic}.DLQ`로 격리
- User/Order DLQ Consumer는 원본 topic, partition, offset, 예외, payload를 master DB에 저장
- 관리자 API는 저장된 원본 payload를 이용해 프로비저닝 또는 상품 스냅샷을 재처리

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.3.2, Spring Web, Spring Security, Spring Data JPA |
| Build | Gradle Wrapper 8.14.3, Gradle Multi-Project |
| Database | MariaDB 10.6, Hibernate Multi-Tenancy, HikariCP, Flyway 9.22.3 |
| Messaging | Apache Kafka, Spring Kafka, Transactional Outbox, DLQ |
| Cache / Stream | Redis 7.2, Redis Lua, Redis Stream, Caffeine |
| Auth / API | JWT(HS256), Springdoc OpenAPI 2.3.0 |
| Observability | Spring Boot Actuator, Micrometer |
| Load test | k6 |

## 프로젝트 구조

```text
spring-msa/
├── auth-service/       # 인증, 테넌트 원장, 프로비저닝 Outbox
├── user-service/       # 사용자 테넌트 스키마 및 사용자 도메인
├── product-service/    # 상품, 상품 옵션, 재고, 상품 스냅샷
├── order-service/      # 멀티테넌트 주문, Redis 재고/Stream, 스냅샷
├── common-lib/         # 서비스 공통 타입과 암호화 코드
├── docker/             # MariaDB, Kafka, Zookeeper, Redis 및 관리 UI
├── load-tests/k6/      # Ramp-up, Stress, Spike, Soak 테스트
├── build.gradle
└── settings.gradle
```

## 로컬 실행

### 사전 요구사항

- JDK 17 이상
- Docker와 Docker Compose
- 선택: k6

### 1. 인프라 실행

저장소 루트에서 다음 명령을 실행한다.

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

| 구성 요소 | 주소 |
|---|---|
| MariaDB | `localhost:3306` (`root` / `1234`) |
| Kafka | `localhost:9092` |
| Kafka UI | <http://localhost:9090> |
| Redis | `localhost:6379` |
| Redis Insight | <http://localhost:5540> |

### 2. 애플리케이션 실행

각 명령을 별도 터미널에서 실행한다. `local` profile을 활성화해야 로컬 MariaDB, Kafka, Redis 설정을 읽는다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew :auth-service:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :user-service:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :product-service:bootRun
SPRING_PROFILES_ACTIVE=local ./gradlew :order-service:bootRun
```

Swagger UI:

- Auth: <http://localhost:8081/api/auth-service/swagger-ui/index.html>
- User: <http://localhost:8082/api/user-service/swagger-ui/index.html>
- Product: <http://localhost:8083/api/product-service/swagger-ui/index.html>
- Order: <http://localhost:8084/api/order-service/swagger-ui/index.html>

Order Actuator-주문 이벤트 워커:

- Health: <http://localhost:8084/api/order-service/actuator/health>
- Metrics: <http://localhost:8084/api/order-service/actuator/metrics>
- Redis Stream Consumer timer: `order.stream.consumer.total`

## API 사용 예시

### 회원가입

```bash
curl -i -X POST http://localhost:8081/api/auth-service/auth/signUp \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": "user01",
    "userPwd": "password",
    "userName": "사용자",
    "userPhone": "010-0000-0000",
    "userAddr": "Seoul",
    "userAddrDetail": "Gangnam"
  }'
```

### 로그인

```bash
curl -X POST http://localhost:8081/api/auth-service/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user01","password":"password"}'
```

응답의 `token`을 `Authorization: Bearer ...`로 전달한다. User/Order 요청에서는 JWT의 `tenantKey` claim과 동일한 `X-Tenant-Id`도 함께 보내야 한다.

```bash
export AUTH_TOKEN='<로그인 응답의 token>'
export TENANT_ID='<JWT의 tenantKey claim>'

curl http://localhost:8082/api/user-service/user/me \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}"
```

### 상품 등록

```bash
curl -i -X POST http://localhost:8083/api/product-service/product \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "productCode": "SKU-001",
    "productName": "테스트 상품",
    "description": "Redis 재고 테스트 상품",
    "brandName": "MSA Shop",
    "productOptionDtos": [
      {
        "optionName": "기본",
        "price": 12000,
        "currency": "KRW",
        "totalQuantity": 100
      }
    ]
  }'
```

상품 생성이 commit되면 옵션별 Redis 재고가 초기화되고, Order Service용 스냅샷 이벤트가 발행된다.

### Redis Stream 주문 접수

`productOptionId`와 `requestUpdateVersion`은 생성된 상품 옵션 값과 일치해야 한다.

```bash
curl -i -X POST \
  http://localhost:8084/api/order-service/order/purchaseProduct/redisOnly \
  -H "Authorization: Bearer ${AUTH_TOKEN}" \
  -H "X-Tenant-Id: ${TENANT_ID}" \
  -H 'Content-Type: application/json' \
  -d '{
    "productOptionId": 1,
    "quantity": 1,
    "requestUpdateVersion": 1
  }'
```

정상 접수되면 `202 Accepted`와 다음 형태의 응답을 반환한다.

```json
{
  "eventId": "...",
  "status": "ACCEPTED"
}
```

## 주요 API

아래 경로 앞에는 각 서비스의 Context path가 붙는다.

| 서비스 | Method | Path | 인증 | 설명 |
|---|---|---|---|---|
| Auth | `POST` | `/auth/signUp` | 불필요 | 회원과 테넌트 생성 |
| Auth | `POST` | `/auth/login` | 불필요 | JWT 발급 |
| Auth | `POST` | `/admin/outbox-events/retry/{eventId}` | Admin | 실패 Outbox 재시도 |
| User | `GET` | `/user/me` | JWT + Tenant | 현재 테넌트 사용자 확인 |
| User | `POST` | `/admin/dlq-events/replay/provision` | Admin | 프로비저닝 DLQ 재처리 |
| Product | `POST` | `/product` | JWT | 상품,옵션,재고 생성 |
| Product | `POST` | `/admin/outbox-events/retry/{eventId}` | JWT | 실패 Outbox 재시도 |
| Order | `GET` | `/order/me` | JWT + Tenant | 현재 테넌트 사용자 확인 |
| Order | `POST` | `/order/purchaseProduct` | JWT + Tenant | Redis 선점 + 동기 DB 주문 |
| Order | `POST` | `/order/purchaseProduct/redisOnly` | JWT + Tenant | Redis Lua + Stream 비동기 주문 |
| Order | `POST` | `/test/order/pessimisticLock` | JWT + Tenant | 비관적 락 부하 테스트 경로 |
| Order | `POST` | `/test/order/conditionalUpdate` | JWT + Tenant | 조건부 update 부하 테스트 경로 |
| Order | `POST` | `/admin/outbox-events/retry/{eventId}` | Admin | 실패 Outbox 재시도 |
| Order | `POST` | `/admin/dlq-events/replay/provision` | Admin | 프로비저닝 DLQ 재처리 |
| Order | `POST` | `/admin/dlq-events/replay/product-snapshot` | Admin | 상품 스냅샷 DLQ 재처리 |

## 문제 해결 사례

### 1. Redis Lua + Stream으로 주문 접수 성능 개선

동기 주문 API의 DB 저장 대기를 Redis Lua 기반 접수와 Redis Stream 비동기 저장으로 분리했다. 동일한 700 TPS Ramp-up 스케줄에서 p95 응답시간을 `7.37초`에서 `14.61ms`로 줄이고, Dropped Iteration을 `4,819건`에서 `0건`으로 개선했다.

- [문제 상황, 대안 비교, 구현, 실패 시나리오와 측정 결과](docs/case-studies/01-redis-lua-stream-order-performance.md)

## 부하테스트

주문 부하 테스트는 [load-tests/k6/README.md](load-tests/k6/README.md)에 실행 방법, 환경 변수, SLO, 측정 결과가 정리되어 있다.

```bash
export AUTH_TOKEN='<JWT>'
export TENANT_ID='<tenantKey>'
k6 run -e ENDPOINTS=redis_purchase load-tests/k6/ramp-up.js
```

지원 시나리오:

- `ramp-up.js`: 단계적으로 TPS를 올려 처리량 한계 탐색
- `stress.js`: 테스트 예정
- `spike.js`: 테스트 예정
- `soak.js`: 테스트 예정

## 현재 제약사항

- 로컬 YAML의 DB 비밀번호, JWT secret, 암호화 master key는 개발 편의를 위한 값이다. 운영에서는 Secret Manager나 환경 변수로 분리해야 한다.
- Redis Stream 실패 메시지는 Pending에 남지만, Pending을 회수하는 `XAUTOCLAIM` worker는 구현 예정
