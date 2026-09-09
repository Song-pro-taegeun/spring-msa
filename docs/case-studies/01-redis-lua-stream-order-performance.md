# Redis Lua + Stream으로 주문 접수 p95를 7.37초에서 14.61ms로 개선(700 TPS)

기존 주문은 Redis에서 재고를 선점한 뒤 같은 요청 안에서 사용자 조회와 주문 DB 저장까지 수행했다. 트래픽이 증가하자 DB 작업대기 요청이 누적 됐고, 700 TPS Ramp-up 테스트에서 p95 응답시간이 7초까지 증가했다.

주문의 사용자 응답 기준을 **DB 저장 완료**에서 **Redis 주문 접수 완료**로 변경했다. Redis Lua 스크립트 하나에서 재고 차감, 구매 원장 기록, 이벤트 멱등성 기록, Redis Stream 이벤트 생성을 처리하고 즉시 성공을 반환했다. 주문 DB 저장은 Stream Consumer가 비동기로 수행하도록 분리했다.

동일한 700 TPS 스케줄에서 다음 결과를 얻었다.

- p95 응답시간: 7,371.99ms → 14.61ms
- 평균 응답시간: 2,823.72ms → 5.47ms
- 완료 요청: 61,671건 → 66,749건
- Dropped Iteration: 4,819건 → 0건
- 최대 활성 VU: 3,884개 → 44개
- HTTP 실패율과 비즈니스 실패율: 두 방식 모두 0%

이 결과는 주문 데이터의 DB 저장 완료시간이 아니라 Redis에 주문을 접수하고 응답하기까지의 시간을 측정 함.

### **1. 문제 상황**

```
기존 주문 흐름: HTTP 주문 요청
→ Redis Lua로 재고 확인 및 차감
→ Tenant DB connection 획득
→ 사용자 조회
→ 주문과 주문 항목 저장
→ DB commit
→ HTTP 200 응답
```

Redis에서 재고를 빠르게 선점하더라도 요청은 Tenant DB 트랜잭션이 끝날 때까지 완료되지 않는다. 부하가 커지면 다음 자원을 함께 점유

- HTTP 요청 처리 thread
- Tenant HikariCP connection
- Hibernate/JPA entity 생성에 필요한 CPU와 heap
- MariaDB의 CPU, memory, disk I/O

DB 처리시간이 길어지면 connection 반환이 늦어지고, 뒤의 요청이 connection pool 앞에서 대기한다. 응답이 늦어진 만큼 k6는 목표 arrival rate를 유지하기 위해 더 많은 VU를 할당한다. VU와 요청이 계속 누적되면 응답시간과 Dropped Iteration이 함께 증가한다. HTTP 오류가 없더라도 시스템이 목표 처리량을 수용했다고 볼 수 없는 상태였다.

### **2. 기존 구현과 보상 처리**

기존 `/order/purchaseProduct`는 Redis Lua로 재고를 선점한 후 Tenant DB에 주문을 동기 저장한다. Redis 재고 차감 이후 DB 저장이 실패하면 선점 당시의 `updateVersion`을 확인하고 재고를 복구한다.

```
Redis 재고 차감 성공
-> Tenant DB 주문 저장 실패
-> 현재 Redis version과 선점 당시 version 비교
-> version이 같으면 재고 복구
```

이 구조는 실패 시 재고를 되돌릴 수 있지만 다음 한계 존재

1. HTTP 요청이 DB commit까지 기다림
2. Redis와 MariaDB는 하나의 트랜잭션으로 묶이지 않음
3. DB 저장 실패뿐 아니라 보상까지 실패하면 별도의 복구가 필요
4. 요청량이 늘면 Tenant DB connection과 요청 thread가 함께 병목

### **3. 개선 목표**

사용자 응답에 반드시 필요한 작업과 나중에 수행할 수 있는 작업을 분리하는 것을 목표로 했다.

#### **HTTP 동기 경로**

요청값 검증 → 상품 version 검증 → 재고 확인 및 차감 → 이벤트 중복 처리 방지 → 사용자별 구매 원장 기록 → DB 반영용 이벤트 저장(Stream)

#### **비동기 경로**

TenantContext 설정 → Tenant DB 사용자 조회 → 주문 및 주문 항목 저장 → DB commit → Stream ACK

**성능 판정 기준**

| **지표** | **기준** |
| --- | --- |
| http_req_duration | p95 < 500ms |
| http_req_failed | rate < 1% |
| business_failures | rate < 1% |
| dropped_iterations | count = 0 |

### **4. 대안 검토**

이 사례의 목표는 재고 동시성 제어 방식의 비교가 아니라 주문 접수 경로의 비동기화이므로, 비관적락, 낙관적락, 조건부 UPDATE는 대안 비교에서 제외했다.

| **대안** | **장점** | **한계** |
| --- | --- | --- |
| DB connection pool 확대 | 구현 변경이 작음 | DB 처리량 자체가 늘지 않으면 대기 위치만 바뀌며 DB 과부하 위험이 커짐 |
| Redis 처리 후 Kafka 직접 발행 | 후속 작업 비동기화 가능 | Redis 차감 성공 후 Kafka 발행 전에 종료되면 이벤트가 유실되는 Dual Write 구간이 생김 |
| Redis 처리 후 DB Outbox 저장 | 기존 Outbox Publisher 재사용 가능 | Redis와 DB Outbox INSERT가 원자적이지 않아 두 작업 사이의 장애를 별도로 복구해야 함 |
| Redis Lua + Redis Stream | 재고 변경과 이벤트 생성을 Redis 안에서 함께 처리하고 빠르게 접수 가능 | Redis durability, Pending 회수, 원장 대사 등 별도 운영 설계 필요 |

선착순 상품처럼 하나의 상품 옵션에 요청이 집중되는 상황을 가정했기 때문에 Redis Lua + Redis Stream을 선택했다. 핵심은 단순히 MariaDB를 Redis로 교체한 것이 아니다. 주문의 완료 의미를 다음처럼 나눴다.

- 접수 완료: Redis 원자 처리와 Stream 이벤트 생성 완료
- 처리 완료: Tenant DB 주문 commit 완료

### **5. Redis Lua를 이용한 주문 접수**

개선된 `/order/purchaseProduct/redisOnly`는 order_accept.lua를 실행한다.

#### **Redis 자료구조**

| **용도** | **Key** | **Type** |
| --- | --- | --- |
| 상품 옵션 재고 | shared:product-service:inventory:product-option:{optionId} | Hash |
| 처리한 이벤트 | order:product-option:{optionId}:events | Hash |
| 사용자별 구매 원장 | order:product-option:{optionId}:ledger | Hash |
| 주문 접수 이벤트 | order:accepted:stream | Stream |

재고 Hash에는 주문 처리에 필요한 snapshot을 함께 저장한다.

#### **Lua 처리 순서**

```
1. 주문 수량과 필수 인자 검증
2. eventId가 이미 처리됐는지 확인
3. 재고 Hash와 필수 필드 확인
4. 요청 version과 현재 version 비교
5. 재고 수량 확인
6. 재고 차감
7. 사용자별 구매 수량 누적
8. Redis Stream에 주문 이벤트 XADD
9. eventId와 Stream ID 매핑
```

성공하면 재고 차감, 원장 변경, XADD, eventId 기록 사이에 다른 Client 명령이 끼어들지 않는다. 별도의 분산 lock 없이 Redis의 단일 실행 경계 안에서 주문을 접수할 수 있다. Lua 실행이 성공하면 Tenant DB 저장을 기다리지 않고 다음 응답을 반환한다(HTTP/1.1 202 Accepted).

### **6. Redis Stream Consumer를 이용한 DB 반영**

Consumer는 다음 순서로 주문을 저장한다.

```
1. 메시지의 tenantKey를 TenantContext에 설정
2. eventId 중복 확인
3. Tenant DB 트랜잭션 시작
4. 사용자 조회, orders와 order_items 저장
5. DB commit
6. XACK
```

Stream key와 Consumer Group은 다음과 같다.

```yaml
order:
  redis-stream:
    key: order:accepted:stream
    group: order-db-writer
    consumer-count: 16
```

16개의 Consumer가 같은 Group에서 메시지를 경쟁 소비하고, 각 Consumer는 별도의 executor thread에서 DB 저장과 ACK를 수행한다. 현재 구현에는 Pending 메시지를 다른 Consumer가 회수하는 `XAUTOCLAIM` worker가 없다.(구현 예정)

### **7. Ramp-up 테스트**

| **항목** | **조건** |
| --- | --- |
| 장비 | MacBook Air, memory 16GB, storage 256GB |
| 배치 | k6, Order Service, Redis, MariaDB, Kafka, Zookeeper, 관리 UI를 한 장비에서 실행 |
| 실행시간 | 약 120초 |
| 비교 API | 동기 `/order/purchaseProduct`, 비동기 `/order/purchaseProduct/redisOnly` |
| Tenant HikariCP | 테스트 대상 테넌트의 `maximumPoolSize=60`, `minimumIdle=0` |
| 테스트 방식 | 각 API를 개별 실행 |
| 목표 SLO | p95 500ms 미만, 오류율 1% 미만, Dropped 0건 |

동기 방식과 Stream 방식은 서로 다른 상품 옵션을 사용하고, 각 테스트가 끝날 때까지 소진되지 않을 만큼 재고를 준비했다. 스케줄상 이론적인 전체 iteration은 약 66,750건이다. 전체 평균 TPS는 상승, 유지, 하강 구간을 모두 포함하므로 최대 목표인 700 TPS보다 낮게 출력된다.

#### **측정 결과**

| **지표** | **Redis Lua + 동기 DB 저장** | **Redis Lua + Stream** | **변화** |
| --- | --- | --- | --- |
| 완료 요청 | 61,671건 | **66,749건** | 5,078건 증가 |
| 평균 완료 처리량 | 513.84 req/s | **556.23 req/s** | 8.25% 증가 |
| 평균 응답시간 | 2,823.72ms | **5.47ms** | 99.81% 감소 |
| 중앙값 | 2,152.77ms | **3.26ms** | 99.85% 감소 |
| p90 | 6,477.34ms | **5.16ms** | 99.92% 감소 |
| p95 | 7,371.99ms | **14.61ms** | 99.80% 감소 |
| 최대 응답시간 | 8,700.70ms | **518.57ms** | 94.04% 감소 |
| Dropped Iteration | 4,819건 | **0건** | 전부 제거 |
| 최대 활성 VU | 3,884개 | **44개** | 98.87% 감소 |
| HTTP 실패율 | 0% | 0% | 동일 |
| 비즈니스 실패율 | 0% | 0% | 동일 |
| p95 SLO | 실패 | **통과** | - |
| Dropped SLO | 실패 | **통과** | - |

HTTP 실패율 0% 라는 결과만 보면 두 방식 모두 성공처럼 보인다. 하지만 동기 방식은 응답 지연으로 VU가 오래 점유됐고, k6가 예정한 시각에 시작하지 못한 iteration이 4,819건 발생했다. 실제로 전송된 요청은 성공했지만 목표 부하를 수용하지 못한 것이다.

### **8. 개선된 이유**

개선 효과는 Redis Lua 자체의 실행속도만으로 설명할 수 없다. 가장 큰 변화는 HTTP critical path에서 DB 작업을 제거한 것이다. DB 작업이 비동기로 이동하면서 요청 thread와 k6 VU가 DB commit을 기다리지 않게 됐다. 그 결과 동시 활성 요청이 줄고, 같은 부하 생성 장비에서도 목표 arrival rate를 유지할 수 있었다. 이것은 DB 작업을 제거한 것이 아니라 실행 시점과 자원 경쟁 위치를 바꾼 것이다. Consumer 처리량이 접수 TPS보다 낮으면 Stream backlog는 계속 증가한다.

### **9. Consumer 수 산정**

```
별도의 Actuator Timer 측정에서 Consumer 처리 결과
처리 건수   = 3,399건
전체 처리시간 = 54.134780718초
건당 평균   = 약 15.93ms

Consumer 하나의 이론상 처리량
1,000ms ÷ 15.93ms
= 약 62.8 TPS
	-> 700 TPS를 처리하기 위한 최소 동시 처리 수는 약 12개
	-> 목표 사용률을 70%로 제한해 지연 편차와 재시도 여유를 두면 약 16개가 필요
```

따라서 Consumer 수를 16개로 설정했다. 이 계산은 DB가 필요한 connection과 처리량을 제공한다는 전제를 가진다. Consumer thread만 늘리고 DB connection이나 DB CPU가 따라오지 못하면 병목이 executor에서 connection pool로 이동할 뿐이다.

기존에는 Redis Stream Consumer를 1개만 운영해 이벤트를 직렬로 처리했다. 그 결과 2분 동안 유입된 66,749건을 같은 시간 안에 처리하지 못했고, 소비 처리량이 이벤트 적재 속도를 따라가지 못하면서 backlog가 계속 증가했다. 이후 Consumer 수를 16개로 늘려 병렬 처리한 결과, 동일한 66,749건을 1분 12초 만에 모두 처리했다. 이를 통해 이벤트 처리량을 크게 높이고, 주문 데이터가 DB에 준 실시간으로 반영되는 것을 확인했다.

### **10. 측정 결과의 한계**

#### **단일 장비에서의 측정**

- k6와 애플리케이션, Redis, MariaDB, Kafka가 같은 장비의 CPU와 memory, disk를 공유했다. 800 TPS 이상에서 관찰한 포화는 Order Service만의 절대 한계라고 볼 수 없다. 물리적, 클라우드 서버의 여유가 있다면, 외부 부하 생성기와 독립된 인프라에서 다시 측정해야 한다.

### **11. 결론**

기존 구조의 병목은 Redis 재고 선점이 아니라 HTTP 요청이 Tenant DB 저장 완료까지 기다리는 데 있었다. Redis Lua 안에서 재고 차감과 주문 이벤트 생성을 함께 수행하고 DB 저장을 Stream Consumer로 분리해 HTTP critical path를 줄였다.

그 결과 동일한 700 TPS Ramp-up 스케줄에서 p95를 7.37초에서 14.61ms로 줄이고, 4,819건의 Dropped Iteration을 제거했다. 

성능 개선의 핵심은 단순히 빠른 저장소를 사용한 것이 아니라, 사용자 응답에 필요한 최소 작업과 최종 데이터 반영 작업의 경계를 다시 정의한 데 있다.
