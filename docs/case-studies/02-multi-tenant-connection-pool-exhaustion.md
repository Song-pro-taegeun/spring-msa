# 멀티테넌트 환경에서 DB 커넥션 풀 고갈을 해결한 과정
## 멀티테넌시 도입 목적

하나의 애플리케이션에서 여러 고객의 커머스 서비스를 제공하되, 고객별 데이터가 서로 섞이지 않도록 Schema-per-Tenant 방식을 적용했다.

공용 테이블에 `tenant_id`를 저장하는 방식은 구현이 단순하지만, 모든 고객의 데이터가 하나의 테이블과 인덱스에 누적된다. 테넌트 식별 조건이 누락되면 다른 고객의 데이터가 조회될 수 있고, 특정 고객의 트래픽과 데이터 증가가 공용 테이블의 인덱스 크기, Lock 경합, 조회 성능에 영향을 줄 수 있다.

이를 방지하기 위해 테넌트마다 별도의 Schema와 DB 계정을 생성했다. Hibernate가 요청의 `tenantKey`에 따라 해당 Schema의 DataSource를 선택하도록 구성해 다음 목적을 달성하고자 했다.

- 고객별 데이터의 논리적·권한적 격리
- 테넌트별 테이블과 인덱스 분리를 통한 경합 범위 축소
- 특정 테넌트의 데이터 증가가 다른 테넌트 테이블에 미치는 영향 제한
- 테넌트별 마이그레이션과 데이터 수명주기 관리
- 필요할 경우 특정 테넌트를 별도 DB 인스턴스로 이전할 수 있는 확장 기반 마련

다만 Schema를 분리하더라도 동일한 MariaDB 인스턴스의 CPU, 메모리, 디스크 I/O와 전체 커넥션 한도는 공유한다. 또한 테넌트별 DataSource와 Connection Pool이 필요해 테넌트 수에 비례하여 DB 커넥션이 증가하는 새로운 문제가 발생했다.

기존 멀티테넌트 구조는 API 요청에 포함된 `tenantKey`에 따라 테넌트별 HikariCP를 생성하고 `ConcurrentHashMap`에 보관했다. 각 풀의 `maximumPoolSize`는 10이었고 `minimumIdle`은 별도로 지정하지 않았다. HikariCP는 `minimumIdle`을 지정하지 않으면 `maximumPoolSize`와 같은 값으로 사용하기 때문에, 접근한 테넌트마다 최대 10개의 DB 커넥션을 유지할 수 있었다.

MariaDB의 `max_connections`는 151이었지만, 한 서비스에서 15개 테넌트에 접근하면 테넌트 풀만으로 최대 150개의 커넥션을 사용할 수 있었다. 여기에 Auth Service, 각 서비스의 Master DataSource, Flyway와 프로비저닝 작업, 관리용 연결이 추가되면서 `Too many connections`가 발생했다.

문제의 원인을 개별 풀의 크기가 아니라 다음 곱셈 구조로 정의했다.

**전체 DB 커넥션 상한 151개**
- Master Pool 30개 (Auth 10 + Product 10 + User 5 + Order 5)
- Flyway·프로비저닝·배치 25개
- DB 관리·모니터링·장애 대응 여유 21개
- 테넌트 전용 Pool 75개

이를 개선하기 위해 테넌트별 풀을 `maximumPoolSize=1`, `minimumIdle=0`으로 변경하고, 테넌트 전용 커넥션 예산을 총 75개로 제한했다. 단일 테넌트 서비스 인스턴스에 이 예산을 모두 할당하는 경우 Caffeine에 최대 75개의 테넌트 DataSource를 보관할 수 있다. 5분간 접근하지 않은 풀은 캐시에서 제거하고, 애플리케이션 종료 시 남아 있는 풀을 모두 닫도록 구성했다.

같은 15개 테넌트를 기준으로 테넌트 커넥션의 설정상 상한은 `150개`에서 `15개`로 90% 감소했다. 전체 테넌트 수가 증가하더라도 테넌트 전용 커넥션은 총예산인 75개를 넘지 않도록 구성했다. 이는 실제 처리량 개선 수치가 아니라 설정으로 제한한 정상 상태의 연결 예산이다.

## 1. 문제 상황

멀티테넌트 요청은 다음 순서로 DB에 접근했다.

```text
검증된 tenantKey를 TenantContext에 저장
→ Hibernate가 getConnection(tenantKey) 호출
→ tenantKey에 해당하는 HikariDataSource 조회
→ 캐시에 없으면 새 풀 생성
→ 테넌트 DB 커넥션 획득
```

요청마다 DataSource를 새로 만들지 않기 위해 테넌트별 풀을 캐시에 저장했다. 그러나 기존 캐시는 크기 제한과 만료 정책이 없는 `ConcurrentHashMap`이었다.

```java
private final Map<String, DataSource> cache = new ConcurrentHashMap<>();

private DataSource createTenantDataSource(String tenantKey) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(mariaDbBaseUrl + info.schema());
    ds.setUsername(info.username());
    ds.setPassword(info.password());
    ds.setMaximumPoolSize(10);
    return ds;
}
```

한 번 접근한 테넌트의 DataSource는 애플리케이션이 종료될 때까지 계속 남았다. 테넌트가 늘어날수록 풀 객체뿐 아니라 각 풀이 유지하는 물리 DB 커넥션도 함께 증가했다.

## 2. 원인 분석

### 테넌트 수에 비례하는 풀

기존 구조에서 한 서비스가 확보할 수 있는 테넌트 커넥션 상한은 다음과 같았다.

```text
15개 테넌트 × 테넌트별 최대 10개 = 최대 150개
```

MariaDB 전체 한도가 151개이므로 Master DataSource나 다른 서비스의 연결이 하나만 추가돼도 한도를 넘을 수 있었다. 서비스 인스턴스가 여러 개라면 각 인스턴스가 자체 캐시와 HikariCP를 가지므로 필요한 연결 수도 인스턴스 수만큼 다시 증가한다.

### minimumIdle 기본값

기존 코드는 `maximumPoolSize=10`만 지정했다. HikariCP의 `minimumIdle` 기본값은 `maximumPoolSize`와 같기 때문에 각 풀은 부하가 없을 때도 최대 10개의 유휴 커넥션을 유지하려고 한다.

따라서 `maximumPoolSize`는 단순히 순간적인 최대치가 아니라, 이 구성에서는 테넌트마다 유지하려는 풀 크기로도 동작했다.

### 종료되지 않는 DataSource

`ConcurrentHashMap`에는 크기 제한과 만료 정책이 없었다. 테넌트 트래픽이 끊겨도 캐시 항목과 HikariDataSource를 제거하거나 `close()`하는 경로가 없었다. 일시적으로 접근한 테넌트도 애플리케이션 수명 동안 연결 예산을 계속 차지할 수 있었다.

## 3. 개선 목표

다음 기준으로 개선 방향을 정했다.

| 목표 | 기준 |
|---|---|
| 유휴 연결 최소화 | `minimumIdle=0`, `maximumPoolSize=1` |
| 풀 개수 제한 | 전체 테넌트 전용 커넥션 예산 최대 75개 |
| 비활성 풀 회수 | 마지막 접근 후 5분 뒤 만료 |
| 무한 대기 방지 | 커넥션 획득 대기시간 5초 |
| 정상 종료 시 정리 | 캐시의 모든 DataSource 종료 |
| DB 보호 | `max_connections=151` 안에서 용도별 예산 분리 |

목표는 DB 연결 수를 무조건 늘리는 것이 아니라, 제한된 DB 자원을 서비스 간에 예측 가능하게 분배하는 것이었다.

## 4. 대안 검토

| 대안 | 장점 | 한계 |
|---|---|---|
| MariaDB `max_connections` 확대 | 애플리케이션 변경 없이 빠르게 장애 지점을 늦출 수 있음 | DB 처리 능력이 그대로라면 CPU·메모리·디스크 I/O 경쟁이 커지고 근본적인 상한이 사라지지 않음 |
| 테넌트별 `maximumPoolSize`만 축소 | 구현 변경이 작고 테넌트 하나가 점유하는 연결을 줄일 수 있음 | 캐시가 무제한이면 테넌트 수가 증가할수록 전체 연결 수도 계속 증가 |
| 하나의 공용 풀에서 스키마 전환 | 풀의 수를 크게 줄일 수 있음 | 테넌트별 DB 계정과 권한 격리 구조를 변경해야 하고, 스키마 전환 누락 시 데이터 격리 위험이 있음 |
| 풀 크기와 캐시 크기를 함께 제한 | 테넌트당 연결 수와 서비스 전체 풀 수를 동시에 제한 | 특정 테넌트의 DB 작업이 몰리면 풀 앞에서 대기하며, 축소된 풀의 적정성 검증이 필요 |

현재 구조는 테넌트마다 별도 DB 계정을 사용해 접근 권한을 분리하고 있었기 때문에, 공용 풀로 전환하기보다 동적 풀의 크기와 개수를 함께 제한하는 방법을 선택했다.

## 5. 테넌트별 HikariCP 축소

테넌트별 DataSource를 다음과 같이 변경했다.

```java
...
ds.setMaximumPoolSize(1);
ds.setMinimumIdle(0);
...
```

`maximumPoolSize=1`은 모든 환경의 정답이 아니다. 현재 DB 규모와 연결 예산을 우선 보호하기 위한 값이다. 특정 테넌트의 동시 DB 작업이 많아지면 요청이 직렬화되므로, 실제 쿼리 시간과 대기시간을 측정해 조정해야 한다.

## 6. Caffeine을 이용한 풀 개수 제한

무제한 `ConcurrentHashMap`을 크기와 만료 정책을 가진 Caffeine Cache로 변경했다.

```java
private final Cache<String, HikariDataSource> cache =
        Caffeine.newBuilder()
                .maximumSize(75)
                .expireAfterAccess(Duration.ofMinutes(5))
                .removalListener((String tenant,
                                  HikariDataSource ds,
                                  RemovalCause cause) -> {
                    if (ds != null && canClose(ds)) {
                        closeDataSource(ds);
                    }
                })
                .build();
```

`maximumSize(75)`는 커넥션 수가 아니라 캐시에 보관하는 HikariDataSource의 수를 제한한다. 테넌트별 `maximumPoolSize=1`과 함께 적용하므로, 단일 인스턴스에 테넌트 예산 75개를 모두 할당한 경우 설정상 최대 커넥션 수도 75개가 된다. Caffeine은 최대 크기를 넘으면 사용 빈도와 최근 사용 이력을 고려해 제거 대상을 선택한다. `expireAfterAccess(5분)`을 통해 장기간 요청이 없는 테넌트 풀도 제거한다.

테넌트 DataSource를 생성하는 서비스나 애플리케이션 인스턴스가 여러 개라면 각 인스턴스에 `maximumSize(75)`를 설정하면 안 된다. 다음 합계가 75개 이내가 되도록 서비스와 인스턴스별 캐시 크기를 나눠야 한다.

캐시에서 참조만 제거하면 HikariCP의 물리 연결이 종료되지 않을 수 있으므로 제거 시 `HikariDataSource.close()`를 호출했다. 애플리케이션 정상 종료 시에도 `@PreDestroy`에서 캐시에 남은 모든 풀을 닫도록 구성했다.

## 7. DB 커넥션 예산 설계

MariaDB의 151개 연결을 애플리케이션이 모두 사용하지 않도록 용도별 예산을 나눴다.
- 로컬 환경에서는 물리적 자원의 한계로 하나의 MariaDB 인스턴스를 사용하고, 도메인 서비스별 Schema를 생성해 데이터를 논리적으로 분리했다.
- 실제 운영 환경에서는 특정 서비스의 DB 부하와 장애가 다른 서비스로 전파되지 않도록 도메인 서비스별로 DB 인스턴스를 분리해야 한다.

| 용도 | 커넥션 예산 |
|---|---:|
| Auth Master DataSource | 10 |
| Product Master DataSource | 10 |
| User Master DataSource | 5 |
| Order Master DataSource | 5 |
| Master Pool 소계 | **30** |
| 테넌트 전용 Pool | 75 |
| Flyway·프로비저닝·배치·향후 서비스 | 25 |
| DB 관리·모니터링·장애 대응 여유 | 21 |
| 합계 | **151** |


## 8. 개선 전후 비교

| 항목 | 기존 | 개선 후 |
|---|---|---|
| 테넌트별 최대 연결 | 10개 | 1개 |
| 테넌트별 최소 유휴 연결 | 최대 풀 크기와 동일 | 0개 |
| DataSource 저장소 | 무제한 `ConcurrentHashMap` | 전체 예산 내 최대 75개의 Caffeine Cache |
| 비활성 풀 만료 | 없음 | 마지막 접근 후 5분 |
| 커넥션 대기 상한 | 기본값 | 5초 |
| 애플리케이션 종료 시 풀 정리 | 없음 | `@PreDestroy`에서 전체 종료 |
| 15개 테넌트 기준 설정상 상한 | 최대 150개 | 정상 상태 최대 15개 |
| 전체 테넌트 풀 예산 | 제한 없음 | 최대 75개 |


## 9. 실패 시나리오와 현재 한계

현재 제거 Listener는 활성 커넥션이 없는 경우에만 DataSource를 닫는다. 사용 중인 풀이 크기 제한이나 만료로 제거되면 `close()`를 건너뛰지만, 해당 풀이 유휴 상태가 된 뒤 다시 종료하는 경로는 없다. 따라서 다음 중 하나를 추가해야 한다.

- 제거된 활성 풀을 별도 큐에 넣고 Active Connection이 0이 되면 종료
- DataSource 생명주기를 캐시 제거 정책과 분리해 중앙에서 관리

## 10. 결론

커넥션 고갈의 원인은 MariaDB의 151개 제한 자체가 아니라, 테넌트가 늘어날 때마다 기본 크기 10의 HikariCP가 제한 없이 생성되는 구조에 있었다. DB의 `max_connections`만 늘리면 장애 시점을 늦출 수 있지만 서비스가 사용할 수 있는 연결의 상한은 계속 예측할 수 없다.

테넌트별 풀 크기를 1로 축소하고 `minimumIdle=0`을 적용해 유휴 연결을 줄였다. 여기에 Caffeine의 크기 제한과 만료 정책을 적용해 한 서비스가 보관하는 테넌트 풀의 수를 제한하고, MariaDB 전체 연결을 용도별 예산으로 분리했다.

이 사례의 핵심은 커넥션 수를 단순히 줄인 것이 아니라, `테넌트 수 × 풀 크기 × 서비스 인스턴스 수`로 증가하던 자원을 DB 전체 용량에서 역산 가능한 구조로 바꾼 데 있다. 다만 활성 상태에서 제거된 DataSource의 최종 종료와 개선 후 부하 측정은 추가로 보완해야 한다.
