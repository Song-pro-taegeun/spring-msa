-- KEYS[1]: 재고 키
-- ARGV[1]: 복구할 수량
-- ARGV[2]: 선점 당시 updateVersion

-- 업데이트 버전 조회
local currentVersion = redis.call(
    'HGET',
    KEYS[1],
    'updateVersion'
)

-- 현재 버전과 재고 선점 당시 버전이 다르면 -1
if tonumber(currentVersion) ~= tonumber(ARGV[2]) then
    return -1
end

return redis.call(
    'HINCRBY',
    KEYS[1],
    'quantity',
    ARGV[1]
)