# 멀티테넌트 환경에서 테넌트 위변조와 잘못된 DB 라우팅을 방지한 과정

## 요약

Schema-per-Tenant 구조에서는 요청마다 접근할 고객의 Schema를 안전하게 결정해야 한다. 그러나 Client가 전달한 `X-Tenant-Id`만 신뢰하면 다른 고객의 `tenantKey`로 헤더를 변경할 수 있고, Tenant 데이터와 Master 데이터를 같은 영속성 경로로 처리하면 Master 쿼리까지 Tenant Schema로 전달될 수 있다.

이를 해결하기 위해 일반 사용자 요청에서는 서명된 JWT의 `tenantKey`와 `X-Tenant-Id`를 비교하고, 검증된 값을 요청 범위의 `TenantContext`에 저장했다. Order Service에서는 Repository Package, `EntityManagerFactory`, `TransactionManager`를 Tenant용과 Master용으로 분리해 `TenantContext`를 변경하지 않고도 접근 경로가 결정되도록 구성했다.

그 결과 다음 규칙을 코드 구조로 강제했다.

- JWT와 요청 헤더의 테넌트가 다르면 Controller 진입 전에 `403 Forbidden`을 반환한다.
- Tenant Repository는 현재 요청의 `tenantKey`에 해당하는 Schema만 사용한다.
- Master Repository는 `TenantContext`와 관계없이 고정된 Master Schema를 사용한다.
- 요청 종료 시 `TenantContext`를 제거해 Thread 재사용에 따른 테넌트 정보 전파를 방지한다.

## 1. 테넌트 생성과 사용자 할당

회원가입 시 Auth Service에서 테넌트 식별자와 서비스별 DB 접속 정보를 생성했다.

```text
회원가입 요청
→ UUID 생성 및 tenantKey 변환
→ Tenant와 사용자 저장
→ 서비스별 DB 계정명과 Credential 생성
→ Credential 암호화
→ 서비스별 Tenant Provision Outbox 이벤트 저장
→ Outbox Publisher가 Kafka 이벤트 발행
→ 각 서비스가 자신의 이벤트를 소비
→ Schema·DB 계정·권한·테이블 생성
```


각 서비스의 Schema에는 서비스명과 `tenantKey`를 함께 사용했다.
- msa_{domainService}_{tenantKey} → 해당 고객의 Schema

Auth Service는 서비스별 DB 계정명과 무작위 비밀번호를 만들고, 비밀번호를 암호화한 뒤 Tenant Provision 토픽에 Outbox 이벤트를 발행한다. 이벤트를 소비한 User Service와 Order Service는 Credential을 복호화하고 다음 작업을 수행했다

```text
테넌트 DB 계정 생성
→ 테넌트 Schema 생성
→ 계정 권한 부여
→ 서비스 Master Schema에 Credential 저장
→ Flyway Migration 실행
→ 최초 사용자 데이터 저장
```

회원이 로그인하면 Auth Service는 사용자의 `tenantKey`를 서명된 JWT Claim에 포함했다. 이후 User Service와 Order Service 요청에는 JWT와 함께 `X-Tenant-Id` 헤더를 전달하도록 했다.


JWT와 `TenantContext`에는 DB 비밀번호를 넣지 않았다. 요청에는 테넌트를 식별하는 `tenantKey`만 전달하고, 실제 DB Credential은 각 서비스의 Master Schema에서 조회하도록 분리했다.

## 2. 문제 상황

### Client가 변경할 수 있는 테넌트 헤더

`X-Tenant-Id`는 Client가 임의로 변경할 수 있다. 요청 헤더만으로 DataSource를 선택하면 사용자가 다른 고객의 `tenantKey`를 전달해 해당 고객의 Schema에 접근할 가능성이 생긴다.

따라서 단순히 헤더가 존재하는지만 확인하는 것이 아니라, 인증 서버가 서명한 JWT의 `tenantKey`와 요청 헤더가 같은지 검증해야 했다.

### 요청 Thread에 남을 수 있는 TenantContext

`TenantContext`는 `ThreadLocal`에 현재 테넌트를 저장한다. Tomcat은 요청 처리가 끝난 Thread를 다음 요청에 재사용하므로 값을 제거하지 않으면 이전 요청의 테넌트 정보가 다음 사용자에게 전달될 수 있다.

### Master 쿼리까지 Tenant Schema로 전달될 가능성

주문과 주문 항목은 고객별 Tenant Schema에 저장하지만 다음 데이터는 Order Service의 Master Schema에 저장한다.

```text
Tenant Schema
→ 주문, 주문 항목처럼 고객별로 격리하는 데이터

Master Schema
→ 상품 Snapshot, Outbox, Inbox, DLQ, 프로비저닝 상태처럼
  서비스 전체에서 공유하거나 운영에 사용하는 데이터
```

Tenant Repository와 Master Repository가 하나의 멀티테넌트 `EntityManagerFactory`를 공유하면 모든 쿼리가 `TenantContext`의 영향을 받는다. 이 경우 Master 데이터를 조회하려는 쿼리도 현재 Tenant Schema로 전달될 수 있다.

## 3. 대안 검토

| 대안 | 장점 | 한계 |
|---|---|---|
| `X-Tenant-Id`만으로 테넌트 선택 | 구현이 단순하고 요청 대상을 명시할 수 있음 | Client가 다른 테넌트 값으로 변경할 수 있음 |
| JWT의 `tenantKey`만 사용 | 일반 사용자 요청에서는 구현이 단순하고 위변조를 방지할 수 있음 | 관리자가 요청 대상 테넌트를 별도로 선택하는 경우에는 별도의 라우팅 규칙이 필요함 |
| 하나의 EntityManagerFactory에서 `TenantContext` 값을 변경 | 기존 JPA 설정을 재사용할 수 있음 | Master 접근 전후로 값을 복원해야 하며 누락 시 잘못된 Schema에 접근할 수 있음 |
| Master와 Tenant 영속성 경로 분리 | Repository의 용도만으로 접근할 Schema가 결정됨 | EntityManagerFactory와 TransactionManager가 분리되어 트랜잭션 경계를 별도로 관리해야 함 |

일반 사용자 요청에서는 JWT와 헤더를 교차 검증하고, DB 접근은 Master와 Tenant 영속성 경로를 분리하는 방법을 선택했다. Client가 전달한 값을 그대로 신뢰하지 않으면서도 Repository의 역할만으로 접근 경로를 명확하게 구분할 수 있기 때문이다.

## 4. JWT와 요청 헤더 교차 검증

Security Filter Chain은 `JwtAuthenticationFilter`를 먼저 실행하고, 그 뒤에 `TenantFilter`를 실행하도록 구성했다.
두 Filter의 책임은 다음과 같이 분리했다.

| 구간 | 책임 | 결과 |
|---|---|---|
| JWT Filter 실행 전 | `Authorization`과 `X-Tenant-Id`가 포함된 요청 수신 | 사용자와 테넌트가 아직 확정되지 않음 |
| `JwtAuthenticationFilter` | JWT 서명과 만료시간을 검증하고 사용자와 권한 추출 | 인증 정보를 `SecurityContext`에 저장 |
| `TenantFilter` | 일반 사용자의 JWT `tenantKey`와 `X-Tenant-Id` 비교 | 접근 가능한 테넌트인지 검증 |
| Tenant Filter 실행 후 | 검증된 요청 대상 `tenantKey`를 `TenantContext`에 저장 | 현재 요청이 사용할 테넌트 확정 |
| 요청 종료 후 | `finally`에서 `TenantContext.clear()` 호출 | 다음 요청으로 테넌트 정보가 전파되는 것을 방지 |

검증 로직은 다음과 같다.

```text
JWT 또는 X-Tenant-Id에 tenantKey가 없음
→ 403 Forbidden

JWT tenantKey와 X-Tenant-Id가 다름
→ 403 Forbidden

JWT tenantKey와 X-Tenant-Id가 같음
→ TenantContext에 tenantKey 저장
→ Controller로 요청 전달
```

`JwtAuthenticationFilter`는 인증을 담당하며 사용자와 권한을 `SecurityContextHolder`에 저장한다. 이 시점에는 사용할 Tenant Schema를 결정하지 않는다. 이후 `TenantFilter`가 인증된 사용자와 요청 대상 테넌트의 관계를 검증하고 `TenantContext`를 설정한다.

필터는 `TenantContext`를 설정한 상태에서 `chain.doFilter()`를 호출한다. 따라서 Controller, Service, Repository가 실행되는 동안에는 동일한 요청 Thread에서 `tenantKey`를 조회할 수 있다. 요청의 성공 여부와 관계없이 값이 제거되도록 `finally`에서 Context를 정리했다.

## 5. Tenant DataSource 선택

`TenantFilter`는 실제 DB Credential을 조회하거나 Connection을 획득하지 않는다. 필터는 `tenantKey`만 준비하고, Tenant용 JPA 경로에서 Hibernate가 Connection을 필요로 할 때 실제 DataSource 선택이 실행된다.

```text
[Tenant Schema 접근]
Tenant Repository 호출
- transactionManager
- entityManagerFactory
- TenantIdentifierResolver가 TenantContext 조회
- MultiTenantConnectionProvider.getConnection(tenantKey)
- tenantKey에 해당하는 HikariDataSource 캐시 조회(Caffeine)
   ├─ Cache Hit: 기존 DataSource 재사용
   └─ Cache Miss: Master Schema에서 암호화된 Credential 조회
                  - Credential 복호화
                  - HikariDataSource 생성 및 캐시 저장
- 해당 Tenant Schema의 Connection 반환

[Master Schema 접근]
Master Repository
→ masterTransactionManager
→ masterEntityManagerFactory
→ baseDataSource
→ Master Schema
```

이 구조에서는 JWT나 요청 헤더로 DB 접속 정보를 직접 전달하지 않는다. 외부 요청에서 확인한 `tenantKey`를 내부 Credential과 연결하고, 테넌트별 DB 계정이 접근할 수 있는 Schema도 제한했다.

## 6. Master와 Tenant 영속성 경로 분리

Order Service에서는 Repository Package, Entity Package, `EntityManagerFactory`, `TransactionManager`를 용도별로 분리했다.

| 구분 | Tenant Schema | Master Schema |
|---|---|---|
| Repository Package | `com.msa.order.repository.tenant` | `com.msa.order.repository.master` |
| Entity Package | `com.msa.order.entity.tenant` | `com.msa.order.entity.master` |
| EntityManagerFactory | `entityManagerFactory` | `masterEntityManagerFactory` |
| TransactionManager | `transactionManager` | `masterTransactionManager` |
| DataSource | `tenantKey`에 따라 동적으로 선택 | 고정된 `baseDataSource` |
| TenantContext 적용 | 적용 | 미적용 |

`@EnableJpaRepositories`에서 Repository Package마다 사용할 Bean을 지정했다.

```java
// Tenant Schema
@EnableJpaRepositories(
    basePackages = "com.msa.order.repository.tenant",
    entityManagerFactoryRef = "entityManagerFactory",
    transactionManagerRef = "transactionManager"
)

// Master Schema
@EnableJpaRepositories(
    basePackages = "com.msa.order.repository.master",
    entityManagerFactoryRef = "masterEntityManagerFactory",
    transactionManagerRef = "masterTransactionManager"
)
```

Tenant용 `TransactionManager`를 `@Primary`로 지정했기 때문에 일반적인 `@Transactional`은 Tenant용 `EntityManagerFactory`를 사용한다. Master 작업은 사용할 TransactionManager를 명시한다.

```java
@Transactional
public void createOrder() {
    // Tenant Repository 사용
}

@Transactional(
    transactionManager = "masterTransactionManager",
    propagation = Propagation.REQUIRES_NEW
)
public void markFailed(Long dlqInfoId) {
    // Master Repository 사용
}
```
하나의 API에서 두 Schema에 접근하더라도 TenantContext 값을 Master Schema 이름으로 변경하지 않고, 호출한 Repository와 TransactionManager에 따라 접근 경로가 나뉜다.

## 7. 성공·실패 시나리오

| 시나리오 | 처리 결과 |
|---|---|
| JWT와 헤더의 `tenantKey`가 일치 | 요청 테넌트의 DataSource를 선택해 처리 |
| JWT와 헤더의 `tenantKey`가 불일치 | Controller 진입 전 `403 Forbidden` 반환 |
| 일반 사용자 요청에 테넌트 정보가 없음 | Controller 진입 전 `403 Forbidden` 반환 |
| Tenant Repository 호출 | `TenantContext`의 `tenantKey`에 해당하는 Schema 접근 |
| Master Repository 호출 | `TenantContext`와 관계없이 `baseDataSource` 사용 |


## 8. 트랜잭션 경계와 한계

`transactionManager`와 `masterTransactionManager`는 서로 독립된 로컬 트랜잭션이다. 두 Schema에 대한 작업을 하나의 원자적 트랜잭션으로 묶어주지 않으므로, 예외 발생 시점과 각 트랜잭션의 커밋 순서에 따라 일부 작업만 반영될 수 있다. 한쪽의 실패가 다른 쪽의 Rollback을 항상 보장하지 않는다.

따라서 두 저장 작업 사이에 최종적 일관성이 필요하면 Outbox 이벤트로 후속 작업을 전달하고, 후속 처리에 실패했을 때 Saga 패턴의 보상 트랜잭션을 실행하도록 설계해야 한다.

또한 `TenantContext`는 HTTP Filter가 없는 Kafka Consumer나 Redis Stream Consumer에서는 자동으로 설정되지 않는다. 비동기 메시지에 포함된 `tenantKey`를 DB 트랜잭션이 시작되기 전에 직접 설정하고, 처리 종료 후 반드시 제거해야 한다.

## 9. 결론

멀티테넌트 DB 라우팅에서 중요한 것은 `tenantKey`를 전달하는 것뿐만 아니라 그 값의 신뢰성과 생명주기, 그리고 Master와 Tenant 데이터의 접근 경계를 함께 보장하는 것이다.

서명된 JWT와 요청 헤더를 교차 검증해 Client가 임의로 다른 테넌트를 선택하지 못하게 했고, `TenantContext`를 요청 범위에서만 유지해 Thread 재사용에 따른 정보 전파를 차단했다. Order Service에서는 Master와 Tenant Repository를 서로 다른 JPA Bean에 연결해 Context 변경 없이도 올바른 Schema가 선택되도록 구성했다.

다만 두 TransactionManager는 하나의 원자적 트랜잭션을 제공하지 않는다. 이 경계를 숨기지 않고 Outbox, 재시도, 보상 트랜잭션이 필요한 영역으로 명시한 것이 이 설계의 핵심이다.

## 관련 구현

- [AuthService](../../auth-service/src/main/java/com/msa/auth/service/auth/AuthService.java)
- [User Service SecurityConfig](../../user-service/src/main/java/com/msa/user/config/SecurityConfig.java)
- [User Service TenantFilter](../../user-service/src/main/java/com/msa/tenant/context/TenantFilter.java)
- [Order Service MultiTenantJpaConfig](../../order-service/src/main/java/com/msa/tenant/config/MultiTenantJpaConfig.java)
- [Order Service MasterJpaConfig](../../order-service/src/main/java/com/msa/tenant/config/MasterJpaConfig.java)
- [Order Service MariaDbMultiTenantConnectionProvider](../../order-service/src/main/java/com/msa/tenant/config/MariaDbMultiTenantConnectionProvider.java)
