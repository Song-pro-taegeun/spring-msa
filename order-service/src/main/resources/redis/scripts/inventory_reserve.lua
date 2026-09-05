-- KEYS[1]: 재고 Hash 키
-- ARGV[1]: 주문 수량
-- ARGV[2]: 클라이언트가 확인한 상품 버전

local requestedQuantity = tonumber(ARGV[1])
local requestedVersion = tonumber(ARGV[2])

-- 잘못된 수량(음수 요청, 0 요청 등)
if not requestedQuantity or requestedQuantity <= 0 then
    return {-2}
end

-- 잘못된 요청 버전
if not requestedVersion then
    return {-2}
end

-- 상품 재고 조회
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

-- 필수 상품 정보 없음
if not quantityValue
    or not versionValue
    or not productIdValue
    or not productOptionIdValue
    or not priceValue
    or not currencyValue then
    return {-1}
end

local currentQuantity = tonumber(quantityValue)
local updateVersion = tonumber(versionValue)

-- 업데이트 버전과 요청버전 비교
if updateVersion ~= requestedVersion then
    return {-3}
end

-- 상품 재고 부족
if currentQuantity < requestedQuantity then
    return {0}
end

-- 조회와 차감을 하나의 Lua 안에서 원자적으로 실행
local remainingQuantity = redis.call(
    'HINCRBY',
    KEYS[1],
    'quantity',
    -requestedQuantity
)

-- 재고 선점
return {
    1,
    productIdValue,
    productOptionIdValue,
    remainingQuantity,
    versionValue,
    priceValue,
    currencyValue
}