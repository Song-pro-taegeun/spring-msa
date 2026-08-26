-- KEYS[1]: 재고 Hash 키
-- ARGV[1]: 주문 수량

local requestedQuantity = tonumber(ARGV[1])

-- 잘못된 수량(음수 요청, 0 요청 등)
if not requestedQuantity or requestedQuantity <= 0 then
    return {-2, 0, 0}
end

-- 상품 재고 조회
local quantityValue = redis.call(
    'HGET',
    KEYS[1],
    'quantity'
)

-- 상품 업데이트 버전 조회
local versionValue = redis.call(
    'HGET',
    KEYS[1],
    'updateVersion'
)

-- 상품 재고 또는 버전 없음
if not quantityValue or not versionValue then
    return {-1, 0, 0}
end

local currentQuantity = tonumber(quantityValue)
local updateVersion = tonumber(versionValue)

-- 상품 재고 부족
if currentQuantity < requestedQuantity then
    return {0, currentQuantity, updateVersion}
end

-- 조회와 차감을 하나의 Lua 안에서 원자적으로 실행
local remainingQuantity = redis.call(
    'HINCRBY',
    KEYS[1],
    'quantity',
    -requestedQuantity
)

-- 재고 선점
return {1, remainingQuantity, updateVersion}