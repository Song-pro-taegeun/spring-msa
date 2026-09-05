-- KEYS[1]: 상품 재고 Hash
-- KEYS[2]: 처리 event Hash
-- KEYS[3]: 사용자 구매 원장 Hash
-- KEYS[4]: 주문 Stream
--
-- ARGV[1]: 요청 수량
-- ARGV[2]: 클라이언트가 확인한 상품 버전
-- ARGV[3]: eventId 이벤트 ID
-- ARGV[4]: tenantKey 테넌트 정보
-- ARGV[5]: userId 유저 ID
-- ARGV[6]: acceptedAt 접수시간

local requestedQuantity = tonumber(ARGV[1])
local requestedVersion = tonumber(ARGV[2])
local eventId = ARGV[3]
local tenantKey = ARGV[4]
local userId = ARGV[5]
local acceptedAt = ARGV[6]

if not requestedQuantity or requestedQuantity <= 0 then
    return {-2}
end

if not requestedVersion
    or not eventId or eventId == ''
    or not tenantKey or tenantKey == ''
    or not userId or userId == ''
    or not acceptedAt then
    return {-2}
end

-- ------------------------------------------------
-- 동일한 eventId로 Lua 실행이 재시도됐는지 확인
local existingStreamId = redis.call(
    'HGET',
    KEYS[2],
    eventId
)

-- 이미 처리된 eventId이면 재고/원장/Stream을 다시 변경하지 않도록 리턴
if existingStreamId then
    return {2, existingStreamId}
end
-- ------------------------------------------------

-- ------------------------------------------------
-- 상품 재고 스냅샷 조회 및 필수 필드 존재 여부 확인
local inventory = redis.call(
    'HMGET',
    KEYS[1],
    'quantity',
    'updateVersion',
    'productId',
    'productOptionId',
    'price',
    'currency'
)

local quantityValue = inventory[1]
local versionValue = inventory[2]
local productIdValue = inventory[3]
local productOptionIdValue = inventory[4]
local priceValue = inventory[5]
local currencyValue = inventory[6]

if not quantityValue
    or not versionValue
    or not productIdValue
    or not productOptionIdValue
    or not priceValue
    or not currencyValue then
    return {-1}
end
-- ------------------------------------------------

-- ------------------------------------------------
-- 정합성 체크
local currentQuantity = tonumber(quantityValue)
local updateVersion = tonumber(versionValue)

-- 데이터 형식 오류
if not currentQuantity or not updateVersion then
    return {-4}
end

-- 버전 불일치
if updateVersion ~= requestedVersion then
    return {-3}
end

-- 재고부족
if currentQuantity < requestedQuantity then
    return {0}
end
-- ------------------------------------------------

-- ------------------------------------------------
-- ledgerField 생성(tenant-a:user1)
local ledgerField = tenantKey .. ':' .. userId

-- 기존 원장 값의 숫자 형식 확인
local purchasedQuantity = redis.call(
    'HGET',
    KEYS[3],
    ledgerField
)

-- 데이터 형식 오류
if purchasedQuantity
    and not tonumber(purchasedQuantity) then
    return {-4}
end
-- ------------------------------------------------


-- 실제 기록 부분!!!
-- 재고차감 -> 원장 기록 -> 스트림 적재 -> 스트림 ID와 이벤트ID를 매핑하여 멱등성 기록
-- ------------------------------------------------
-- 재고 차감(요청값을 기존 재고에서 차감)
-- KEYS[1] -> 상품 재고 해시
local remainingQuantity = redis.call(
    'HINCRBY',
    KEYS[1],
    'quantity',
    -requestedQuantity
)
-- ------------------------------------------------

-- ------------------------------------------------
-- 사용자 구매 원장 해시에 누적 구매 수량 증가
-- 구매 요청 수량을 더한다(해당 필드가 없는경우 신규 생성)
-- KEYS[3] -> 원장 해시
redis.call(
    'HINCRBY',
    KEYS[3],
    ledgerField,
    requestedQuantity
)
-- ------------------------------------------------

-- ------------------------------------------------
-- 주문 접수 이벤트 적재
local streamId = redis.call(
    'XADD', -- Redis Stream에 새로운 메시지를 추가
    KEYS[4], -- Redis Stream 키
    '*', -- Stream 메시지 ID를 자동으로 생성(생성 예시 - 1757051234567(밀리초)-0(순번))
    'eventId', eventId,
    'tenantKey', tenantKey,
    'userId', userId,
    'productId', productIdValue,
    'productOptionId', productOptionIdValue,
    'quantity', ARGV[1],
    'price', priceValue,
    'currency', currencyValue,
    'updateVersion', versionValue,
    'acceptedAt', acceptedAt
)
-- ------------------------------------------------

-- ------------------------------------------------
-- event 멱등성 기록
-- eventId와 Stream 메시지 ID 매핑
redis.call(
    'HSET',
    KEYS[2],
    eventId, -- 필드
    streamId -- 값
)

return {
    1,
    streamId,
    remainingQuantity
}