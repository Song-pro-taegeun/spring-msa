# Order service k6 부하 테스트

아래 네 실행 파일이 세 API를 대상으로 각각의 부하 테스트를 수행한다. 인증, 요청 생성, 공통 메트릭은 `common/order-test-common.js`에 모아 중복을 제거했다.

| 실행 파일 | 목적 |
|---|---|
| `ramp-up.js` | 처리량 한계 탐색 |
| `stress.js` | 한계 이상에서 실패 양상 확인 |
| `spike.js` | 순간 5,000 TPS 이후 복구 확인 |
| `soak.js` | 안전 TPS를 장시간 유지해 누수 확인 |


## 실행 전 준비

이 테스트는 실제 재고를 차감한다. 테스트 전에 ID `10`, `20`의 DB 재고와 ID `37`의 Redis 재고/상품 스냅샷 버전, tenant 사용자 데이터를 준비해야 한다. 재고가 먼저 소진되면 서버 성능 문제가 아니어도 `business_failures`가 증가한다. 테스트를 반복할 때는 재고와 주문 데이터를 동일한 초기 상태로 되돌린다.

필요 재고는 대략 `TPS × 지속 시간(초) × quantity`이다. 필요한 재고는 다음 수준이다.

| 테스트 | 전체 요청 수(근사) |
|---|---:|
| Ramp | 32,750 |
| Stress (`LIMIT_TPS=2000`) | 1,815,000 |
| Spike | 270,000 |
| Soak (`SAFE_TPS=500`, 30분) | 900,000 |

토큰은 저장소에 커밋하지 않고 셸 변수로 전달한다. 제공된 JWT는 만료 시간이 있으므로 만료된 경우 새 토큰을 발급한다.

```bash
export AUTH_TOKEN='JWT'

export TENANT_ID='7bbdc9de_a2b8_4cf2_9f31_dbd636121269'

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

# redis + lua API
REPORT_TIME=$(date +%Y%m%d-%H%M%S)

K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_PORT=-1 \
K6_WEB_DASHBOARD_EXPORT="load-tests/k6/results/ramp-up-purchase-${REPORT_TIME}.html" \
k6 run \
  -e ENDPOINTS=purchase \
  load-tests/k6/ramp-up.js
  
```

## 1. Ramp-up: 처리량 한계 탐색

기본값은 총 50 TPS에서 시작해 100 → 200 → 300 → 400 → 500 TPS로 올린다. 각 목표까지 10초 동안 상승하고 10초간 유지한 뒤, 마지막 20초 동안 0 TPS로 낮춘다. 전체 실행시간은 2분이다.

`http_req_duration`, `http_req_failed`, `business_failures`, `dropped_iterations`를 함께 보고 최초로 SLO가 깨지는 구간 바로 아래를 안전 TPS 후보로 잡는다.

## 2. Stress: 한계 이상에서 실패 양상 확인


## 3. Spike: 순간 5,000 TPS와 복구 확인


## 4. Soak: 안전 TPS 장시간 유지


## 주요 설정

| 환경변수 | 기본값 | 설명 |
|---|---:|---|
| `BASE_URL` | `http://localhost:8084/api/order-service` | Order service base URL |
| `ENDPOINTS` | `all` | `all` 또는 `pessimistic,conditional,purchase`의 부분 목록 |
| `SAFE_TPS` | `500` | Stress 복구, Spike 전후, Soak의 총 TPS |
| `LIMIT_TPS` | `2000` | Stress 기준 한계 TPS |
| `SPIKE_TPS` | `5000` | Spike peak 총 TPS |
| `P95_MS` | `500` | p95 latency threshold(ms) |
| `MAX_ERROR_RATE` | `0.01` | HTTP/업무 실패율 상한(1%) |
| `PRE_ALLOCATED_VUS` | 자동 계산 | 일반 구간에서 미리 확보할 VU 수 |
| `SPIKE_PRE_ALLOCATED_VUS` | 자동 계산 | Spike peak 전 미리 확보할 VU 수 |
| `MAX_VUS` | `10000` | 동적으로 늘릴 수 있는 최대 VU 수 |
| `HTTP_TIMEOUT` | `30s` | 요청 timeout |

## 테스트 결과 출력

모든 실행 파일은 테스트 종료 시 `handleSummary()`를 호출한다. 콘솔에는 전체 및 endpoint별 요청 처리율, 성공 건수, HTTP/업무 실패율, 평균/p95 응답시간, dropped iteration과 threshold 결과가 표로 출력된다.

동일한 원본 k6 지표는 기본적으로 `load-tests/k6/results/<테스트>-<시각>.json`에도 저장된다. 저장 위치는 `SUMMARY_DIR`, JSON 저장 여부는 `SUMMARY_EXPORT`로 변경할 수 있다.
