-- Redis 재고 초기화 스크립트
-- Redis에 저장된 현재 버전보다 새 이벤트의 버전이 높을 때만 수량과 버전을 원자적으로 갱신

-- redis.call()은 Lua에서 Redis 명령어를 실행
-- KEYS[1]: Redis 재고 키
-- ARGV[1]: quantity
-- ARGV[2]: updateVersion
-- ARGV[3]: productId
-- ARGV[4]: productOptionId
-- ARGV[5]: price
-- ARGV[6]: currency

-- redis.call('HGET', key, field)
    -- HGET key field
    -- 키와 필드가 모두 존재하면 해당 필드값을 반환
    -- 키 또는 필드가 없으면 false를 반환

-- Redis에 저장된 현재 버전 조회
local currentVersion = redis.call(
    'HGET',
    KEYS[1],
    'updateVersion'
)

-- ARGV 값은 기본적으로 문자열임 비교를 위해 숫자로 변환
local incomingVersion = tonumber(ARGV[2])


-- 기존 버전이 있는지 확인 -> Redis에 저장된 현재 버전이 새로 전달된 버전보다 크거나 같은지 확인
if currentVersion
    and tonumber(currentVersion) >= incomingVersion then
    return 0 -- 동일하거나 과거 버전이므로 무시
end

-- 재고 저장
redis.call(
    'HSET',
    KEYS[1],
    'quantity', ARGV[1],
    'updateVersion', ARGV[2],
    'productId', ARGV[3],
    'productOptionId', ARGV[4],
    'price', ARGV[5],
    'currency', ARGV[6]
)

return 1 -- 신규 등록 또는 최신 버전으로 갱신