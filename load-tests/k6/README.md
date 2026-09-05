# Order service k6 부하 테스트

네 실행 파일이 주문 관련 API를 대상으로 각각의 부하 테스트를 수행한다. 인증, 요청 생성, endpoint 선택, 공통 메트릭은 `common/order-test-common.js`에 모아 중복을 제거했다.

| 실행 파일 | 목적 |
|---|---|
| `ramp-up.js` | TPS를 단계적으로 높여 처리량 한계 탐색 |
| `stress.js` | 확인된 한계 이상에서 실패 양상과 복구 여부 확인 |
| `spike.js` | 순간 5,000 TPS 유입 후 복구 여부 확인 |
| `soak.js` | 안전 TPS를 장시간 유지하며 메모리 누수와 성능 저하 확인 |

## 테스트 대상

| `ENDPOINTS` 값 | API | 상품 옵션 ID | 처리 방식 |
|---|---|---:|---|
| `pessimistic` | `/test/order/pessimisticLock` | 10 | DB 비관적 락 재고 차감 + 동기 주문 저장 |
| `conditional` | `/test/order/conditionalUpdate` | 20 | DB 조건부 업데이트 재고 차감 + 동기 주문 저장 |
| `purchase` | `/order/purchaseProduct` | 40 | Redis Lua 재고 선점 + 동기 주문 DB 저장 |
| `redis_purchase` | `/order/purchaseProduct/redisOnly` | 41 | Redis Lua 재고 선점·구매 원장·Stream 이벤트 생성 후 즉시 응답 |

`ENDPOINTS=all`을 사용하면 선택된 endpoint에 총 목표 TPS를 라운드로빈으로 분배한다. 각각의 endpoint에 목표 TPS가 전부 적용되는 것이 아니다.

## 문제 해결 요약

기존 주문 API는 Redis Lua로 상품 재고를 선점한 뒤 같은 HTTP 요청에서 tenant 사용자 조회와 주문·주문 항목의 DB 저장까지 동기 처리했다. 부하가 증가하자 DB 작업을 기다리는 요청이 누적되면서 700 TPS Ramp-up에서 p95 500ms SLO를 충족하지 못했다.

이를 개선하기 위해 Redis Lua 하나에서 재고 차감, 사용자별 구매 원장 기록, DB 반영용 Redis Stream 이벤트 생성을 원자적으로 수행하고, Stream 생성이 성공하면 즉시 접수 응답을 반환하도록 HTTP 임계 경로를 축소했다. 주문 DB 저장은 Consumer가 `eventId`를 기준으로 멱등하게 처리하며, DB commit 이후 XACK한다. 그 결과 동일한 700 TPS 스케줄에서 동기 DB 저장 방식은 p95 7.37초와 dropped 4,819건으로 실패했지만, Redis Lua + Stream 방식은 p95 14.61ms, dropped 0건, HTTP/업무 실패율 0%로 통과했다.

다만 이 결과는 주문의 DB 저장 완료시간이 아니라 Redis에 주문 접수를 확정하기까지의 응답시간을 개선한 것이다. 전체 시스템의 성능과 정합성을 입증하려면 Consumer 처리 TPS, Stream lag, 접수부터 DB commit까지의 지연시간, 장애 후 재처리와 중복 방지를 별도로 검증해야 한다.

## 실행 전 준비

이 테스트는 실제 재고를 차감하고 주문 데이터를 생성한다. 테스트 전에 각 상품 옵션의 DB 또는 Redis 재고, 상품 버전, tenant 사용자 데이터를 준비해야 한다. 재고가 먼저 소진되면 서버 성능 문제가 아니어도 `business_failures`가 증가한다. 테스트를 반복할 때는 재고와 주문 데이터를 동일한 초기 상태로 되돌리고, 비교 대상마다 가능한 한 동일한 서버 상태를 사용한다.

필요 재고의 기본 계산식은 다음과 같다.

```text
필요 재고 = 스케줄에 따라 생성되는 전체 iteration × 요청당 quantity
```

현재 700 TPS Ramp-up 스케줄의 이론상 총 iteration은 약 66,750건이다. 단일 endpoint를 `quantity=1`로 테스트한다면 최소 66,750개가 필요하며, 재시도와 반복 실행을 고려해 여유 재고를 준비한다.

| 테스트 | 전체 요청 수(근사) |
|---|---:|
| 현재 Ramp-up 700 TPS | 66,750 |
| Stress |  |
| Spike|  |
| Soak|  |

토큰은 저장소에 커밋하지 않고 셸 환경변수로 전달한다. JWT는 만료될 수 있으므로 테스트 직전에 유효성을 확인한다.

```bash
export AUTH_TOKEN='JWT'
export TENANT_ID='7bbdc9de_a2b8_4cf2_9f31_dbd636121269'
mkdir -p load-tests/k6/results
```

## Ramp-up 개별 실행 및 HTML 결과 저장

아래 명령을 한 번에 붙여 넣으면 셸은 첫 번째 `k6 run`이 종료된 후 다음 명령을 순서대로 실행한다. 각 결과 파일에는 시각과 endpoint 이름이 포함되므로 서로 덮어쓰지 않는다.

```bash
# 비관적 락
REPORT_TIME=$(date +%Y%m%d-%H%M%S)

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_EXPORT="load-tests/k6/results/ramp-up-pessimistic-${REPORT_TIME}.html" \
k6 run \
  -e ENDPOINTS=pessimistic \
  load-tests/k6/ramp-up.js

sleep 30

# 조건부 업데이트
REPORT_TIME=$(date +%Y%m%d-%H%M%S)

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_EXPORT="load-tests/k6/results/ramp-up-conditional-${REPORT_TIME}.html" \
k6 run \
  -e ENDPOINTS=conditional \
  load-tests/k6/ramp-up.js

sleep 30

# Redis Lua 재고 선점 + 동기 DB 주문 저장
REPORT_TIME=$(date +%Y%m%d-%H%M%S)

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_EXPORT="load-tests/k6/results/ramp-up-purchase-${REPORT_TIME}.html" \
k6 run \
  -e ENDPOINTS=purchase \
  load-tests/k6/ramp-up.js

sleep 30

# Redis Lua 재고 선점·원장·Stream 생성 후 즉시 응답
REPORT_TIME=$(date +%Y%m%d-%H%M%S)

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_EXPORT="load-tests/k6/results/ramp-up-redis-purchase-${REPORT_TIME}.html" \
k6 run \
  -e ENDPOINTS=redis_purchase \
  load-tests/k6/ramp-up.js
```

## 1. Ramp-up: 처리량 한계 탐색

현재 기본 스케줄은 50 TPS에서 시작해 500 → 600 → 650 → 700 TPS로 높이고, 700 TPS를 30초간 유지한 뒤 20초 동안 0 TPS로 낮춘다. 전체 실행시간은 2분이다. 마지막 20초는 0 TPS 유지 구간이 아니라 700에서 0으로 내려가는 Ramp-down 구간이다.

| 구간 | 지속시간 |
|---|---:|
| 50 -> 500 TPS | 10초 |
| 500 TPS 유지 | 10초 |
| 500 -> 600 TPS | 10초 |
| 600 TPS 유지 | 10초 |
| 600 -> 650 TPS | 10초 |
| 650 TPS 유지 | 10초 |
| 650 -> 700 TPS | 10초 |
| 700 TPS 유지 | 30초 |
| 700 -> 0 TPS | 20초 |

### 판정 기준

| 지표 | 기준 | 의미 |
|---|---:|---|
| `http_req_duration` | p95 < 500ms | 완료된 HTTP 요청 95%의 응답시간 |
| `http_req_failed` | rate < 1% | 실제 전송된 HTTP 요청의 실패율 |
| `business_failures` | rate < 1% | HTTP 성공 여부와 업무 성공 응답을 합친 실패율 |
| `dropped_iterations` | count = 0 | k6가 예정한 시각에 시작하지 못한 iteration 수 |

`dropped_iterations`는 서버가 HTTP 오류를 반환한 건수가 아니다. 응답 지연으로 VU가 오래 점유되거나 `maxVUs`에 도달해 k6가 예정된 요청 자체를 시작하지 못한 수다. 따라서 `http_req_failed=0%`여도 dropped가 발생하면 목표 TPS를 수용했다고 판단할 수 없다.

또한 Ramp-up 전체의 `http_reqs/s`는 상승·유지·하강 구간을 모두 합친 평균이다. 예를 들어 목표가 700 TPS여도 전체 평균이 700 req/s로 출력되지는 않는다.

## Ramp-up 측정 결과

아래 결과는 로컬 단일 장비에서 endpoint를 하나씩 실행한 값이다. 700 TPS 동기 방식은 두 번 모두 실패했으며, 표에는 재측정 결과 중 상대적으로 양호했던 최신 값을 사용했다.

| Peak TPS | 처리 방식 | 완료 요청 | Dropped | 평균 응답시간 | p95 | 최대 활성 VU | 판정 |
|---:|---|---:|---:|---:|---:|---:|---|
| 500 | Redis Lua + 동기 DB 저장 | 32,749 | 0 | 19.49ms | 73.16ms | 164 | 통과 |
| 500 | Redis Lua + Stream 즉시 응답 | 32,749 | 0 | 5.09ms | 9.07ms | 7 | 통과 |
| 700 | Redis Lua + 동기 DB 저장 | 61,671 | 4,819 | 2.82s | 7.37s | 3,884 | 실패 |
| 700 | Redis Lua + Stream 즉시 응답 | 66,749 | 0 | 5.47ms | 14.61ms | 44 | 통과 |
| 800 | Redis Lua + 동기 DB 저장 | 50,427 | 14,029 | 5.10s | 15.26s | 8,250 | 실패 |
| 800 | Redis Lua + Stream 즉시 응답 | 68,754 | 3,495 | 1.19s | 3.70s | 2,598 | 실패 |
| 1,000 | Redis Lua + 동기 DB 저장 | 43,336 | 23,920 | 6.05s | 28.04s | 9,945 | 실패 |
| 1,000 | Redis Lua + Stream 즉시 응답 | 62,329 | 10,401 | 3.29s | 10.04s | 6,305 | 실패 |

### 700 TPS 비교 결과

Redis Lua 실행 후 주문 DB 저장까지 동기 처리한 방식은 완료된 요청 자체의 HTTP/업무 실패율은 0%였지만, p95가 7.37초까지 증가했고 4,819개의 iteration이 시작되지 못했다. 요청 스레드와 VU가 DB 작업 완료를 기다리는 동안 누적되어 목표 처리율을 유지하지 못한 것이다.

Redis Lua에서 재고 차감, 사용자별 구매 원장 기록, DB 반영용 Stream 이벤트 생성을 원자적으로 처리하고 즉시 응답한 방식은 66,749건을 처리하면서 dropped 0건, HTTP/업무 실패율 0%, p95 14.61ms를 기록했다. 같은 700 TPS 스케줄에서 동기 DB 저장 방식 대비 p95가 약 99.8% 감소했고, 최대 활성 VU는 3,884개에서 44개로 감소했다.

이 결과에서 확인한 현재 로컬 환경의 구간은 다음과 같다.

- 500 TPS에서는 두 방식 모두 SLO를 통과했다.
- 700 TPS에서는 동기 DB 저장 방식은 실패하고 Redis Lua + Stream 즉시 응답 방식은 통과했다.
- 800 TPS와 1,000 TPS에서는 두 방식 모두 실패했지만 Redis Lua + Stream 방식이 동기 DB 저장 방식보다 낮은 지연과 dropped를 보였다.
- 현재 환경에서 동기 방식의 변곡점은 500~700 TPS 사이, Redis 즉시 응답 방식의 변곡점은 700~800 TPS 사이에서 관찰됐다.

## 결과의 해석 범위

Redis Lua + Stream endpoint의 HTTP p95는 다음 동기 구간만 측정한다.

```text
HTTP 요청
→ Redis Lua 재고 확인 및 차감
→ 사용자별 구매 원장 기록
→ Redis Stream XADD
→ 접수 응답
```

따라서 p95 14.61ms는 주문의 DB 저장 완료시간이 아니라 **주문 접수 확정시간**이다. Redis Stream Consumer가 주문을 DB에 반영하는 시간은 별도의 비동기 처리 성능 지표로 검증해야 한다. 이는 작업을 제거한 것이 아니라 HTTP 임계 경로에서 분리해 처리량과 실패 복구를 독립적으로 제어한 구조다.

Consumer의 기본 처리 흐름은 다음과 같다.

```text
XREADGROUP
→ event의 tenantKey로 TenantContext 설정
→ eventId를 기준으로 멱등성 확인 및 tenant DB 주문 저장
→ DB commit
→ XACK
→ TenantContext.clear()
```

DB commit 이후 XACK 이전에 Consumer가 종료되면 이벤트가 다시 전달될 수 있으므로 DB의 `eventId`에 유일 제약을 두어 중복 주문 생성을 막는다. Pending 회수, 재시도, DLQ, Stream 보존 정책, Redis 원장과 DB의 누락 비교도 함께 구성해야 최종 정합성을 검증할 수 있다.

## Consumer 동시 처리 수 계산

Consumer worker 수는 Little's Law를 이용해 다음과 같이 근사할 수 있다. 처리시간의 단위가 밀리초라면 반드시 1,000으로 나눈다.

```text
이론상 동시 처리 수 = 목표 Consumer TPS × 평균 DB 트랜잭션 처리시간(ms) ÷ 1,000
```

700 TPS에서 이벤트 하나의 전체 DB 트랜잭션 처리시간이 평균 20ms라면 다음과 같다.

```text
700 × 20 ÷ 1,000 = 14개
```

14개는 지연 편차와 재시도를 고려하지 않은 이론값이다. 약 50%의 여유를 적용하면 초기 worker 수는 20~24개가 적절한 출발점이다. 여기서 20ms는 단일 SQL 실행시간이 아니라 사용자 조회, 주문·주문 항목 저장, commit까지의 이벤트 1건당 전체 DB 트랜잭션 시간을 사용한다.

| 평균 DB 처리시간 | 700 TPS에 필요한 이론상 동시 처리 수 |
|---:|---:|
| 10ms | 7개 |
| 20ms | 14개 |
| 50ms | 35개 |
| 100ms | 70개 |

DB 작업 동안 worker 하나가 connection 하나를 점유한다면 사용 가능한 DB connection 수도 worker 수 이상이어야 한다. 다만 Hikari `maximumPoolSize`는 애플리케이션 인스턴스마다 생성되므로, 인스턴스 수를 늘릴 때는 `인스턴스 수 × pool size`가 DB의 전체 connection 한도를 넘지 않도록 계산한다.

Consumer는 전용 bounded executor를 사용하고 API 요청 executor와 분리한다. 트래픽이 밀릴 때 worker를 무제한 생성하는 대신 Stream lag가 증가하도록 두어 JVM heap과 DB connection을 보호한다. API와 Consumer가 같은 JVM에서 CPU·heap·DB pool을 경쟁하면 HTTP p95에도 영향을 줄 수 있으므로, 트래픽이 큰 환경에서는 API와 Consumer를 별도 프로세스 또는 별도 배포 단위로 분리하고 독립적으로 확장하는 편이 안전하다.

## 로컬 테스트 환경의 한계

현재 테스트는 MacBook Air 16GB/256GB 한 대에서 k6 부하 생성기, Order Service JVM, MariaDB, Redis, Kafka 등 여러 프로세스를 동시에 실행했다. 700 TPS 이상의 실험에서 CPU 포화가 관찰됐으므로 800·1,000 TPS 실패를 Redis 또는 애플리케이션의 절대 처리 한계로 단정할 수 없다.

응답이 느려지면 k6의 arrival-rate executor가 더 많은 VU를 할당하고, 같은 장비의 k6가 추가 CPU를 사용하면서 서버에 사용할 CPU가 더 줄어드는 피드백이 발생할 수 있다. 따라서 현재 결과는 아키텍처 간 병목 차이를 찾은 로컬 통합 실험으로 해석한다.

정확한 용량을 확인하려면 다음 단계에서 부하 생성기와 서버를 서로 다른 장비로 분리하고 동일 사양·동일 초기 데이터로 재측정한다. k6 결과만으로 Tomcat thread 포화나 특정 자원의 병목을 확정할 수 없으므로 아래 서버 지표를 함께 수집한다.

- 애플리케이션 CPU, load average, JVM heap, GC pause
- Tomcat `threads.busy`, `threads.current`, `threads.config.max`, connection 수
- Hikari active, idle, pending, connection acquire time
- Redis CPU, latency, command 처리량, Slow Log
- Consumer 처리 TPS, executor active/queue/rejected 수
- Redis Stream lag, Pending 수, oldest pending age
- 이벤트 접수 시각부터 DB commit까지의 평균/p95/p99

## 후속 검증

Redis Lua + Stream 구조의 완료 조건은 HTTP p95 통과만이 아니다. 다음 테스트를 별도로 수행한다.

1. Consumer를 중지한 상태에서 주문을 접수하고 Stream lag가 안전하게 누적되는지 확인한다.
2. Consumer를 재시작해 모든 `eventId`가 DB에 반영되고 중복 주문이 생성되지 않는지 확인한다.
3. 접수 TPS보다 Consumer TPS가 충분히 큰지, 정상 부하에서 lag가 0으로 회복되는지 측정한다.
4. DB 장애, Consumer 강제 종료, commit 후 XACK 전 종료를 재현해 재처리와 멱등성을 검증한다.
5. Redis 원장의 재고 차감량, Stream 이벤트 수, DB 주문 수를 비교해 누락과 중복을 검증한다.
6. 안전 TPS에서 Soak 테스트를 수행해 JVM heap, GC, Stream lag, DB connection 사용량이 시간에 따라 증가하지 않는지 확인한다.

## 2. Stress: 한계 이상에서 실패 양상 확인

Ramp-up에서 확인한 안전 TPS와 변곡점을 기준으로 한계 이상의 부하를 가한다. 단순 실패 건수뿐 아니라 dropped 발생 시점, p95/p99 증가 시점, Consumer lag 증가율, 부하 제거 후 정상 수준으로 돌아오는 시간을 함께 확인한다.

## 3. Spike: 순간 5,000 TPS와 복구 확인

순간 부하 동안 접수 계층이 오류 대신 Stream backlog로 압력을 흡수하는지 확인한다. Spike 종료 후 HTTP 지연시간이 정상화되고 Consumer가 backlog를 유실이나 중복 없이 모두 처리하는지 확인한다.

## 4. Soak: 안전 TPS 장시간 유지

Ramp-up과 Stress 결과로 정한 안전 TPS를 30분에서 수 시간 유지한다. API 메모리뿐 아니라 Consumer heap, executor queue, Stream Pending, DB connection과 accepted-to-committed latency가 시간에 따라 계속 증가하는지 확인한다.

## 주요 설정

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `BASE_URL` | `http://localhost:8084/api/order-service` | Order Service base URL |
| `ENDPOINTS` | `all` | `all` 또는 endpoint key의 쉼표 구분 목록 |
| `SAFE_TPS` | `700` | Stress 복구, Spike 전후, Soak의 총 TPS |
| `LIMIT_TPS` | `2000` | Stress 기준 한계 TPS |
| `SPIKE_TPS` | `5000` | Spike peak 총 TPS |
| `P95_MS` | `500` | p95 latency threshold(ms) |
| `MAX_ERROR_RATE` | `0.01` | HTTP/업무 실패율 상한(1%) |
| `PRE_ALLOCATED_VUS` | 자동 계산 | 일반 구간에서 미리 확보할 VU 수 |
| `SPIKE_PRE_ALLOCATED_VUS` | 자동 계산 | Spike peak 전 미리 확보할 VU 수 |
| `MAX_VUS` | `10000` | 동적으로 늘릴 수 있는 최대 VU 수 |
| `HTTP_TIMEOUT` | `30s` | 요청 timeout |

`PRE_ALLOCATED_VUS`의 기본 계산은 `SAFE_TPS × P95_MS × 1.2 ÷ 1,000`이며 최소 100이다. 현재 `SAFE_TPS=700`, `P95_MS=500`이면 420 VU를 미리 할당한다.

## 테스트 결과 출력

모든 실행 파일은 테스트 종료 시 `handleSummary()`를 호출한다. 콘솔에는 전체 및 endpoint별 요청 처리율, 성공 건수, HTTP/업무 실패율, 평균/p95 응답시간, dropped iteration과 threshold 결과가 표로 출력된다.

동일한 원본 k6 지표는 기본적으로 `load-tests/k6/results/<테스트>-<시각>.json`에도 저장된다. 저장 위치는 `SUMMARY_DIR`, JSON 저장 여부는 `SUMMARY_EXPORT`로 변경할 수 있다. HTML 대시보드 export에는 endpoint와 `REPORT_TIME`을 파일명에 포함해 각 실행 결과가 덮어쓰이지 않도록 한다.
