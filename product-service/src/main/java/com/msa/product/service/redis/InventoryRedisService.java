package com.msa.product.service.redis;

import com.msa.product.event.internal.InventoryItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InventoryRedisService {
    private static final String INVENTORY_KEY_PREFIX = "shared:product-service:inventory:product-option:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> initializeInventoryScript;
    private final RedisFailureHistoryService redisFailureHistoryService;

    // RequiredArgsConstructor Lombok 생성자에서 @Qualifier를 생성자 파라미터에 기본적으로 복사한다고 보장할 수 없다.
    // 따라서, 명시적 생성자 주입 필요
    public InventoryRedisService(
            StringRedisTemplate redisTemplate,
            @Qualifier("initializeInventoryScript")
            RedisScript<Long> initializeInventoryScript,
            RedisFailureHistoryService redisFailureHistoryService
    ) {
        this.redisTemplate = redisTemplate;
        this.initializeInventoryScript = initializeInventoryScript;
        this.redisFailureHistoryService = redisFailureHistoryService;
    }

    /**
     * 제품 등록 시 레디스에 재고 등록
     * @param items
     */
    public void initializeInventories(List<InventoryItem> items){
        for (InventoryItem item : items) {
            String key = INVENTORY_KEY_PREFIX + item.productOptionId();

            try {
                Long result = redisTemplate.execute(
                        initializeInventoryScript,
                        List.of(key), // KEY
                        String.valueOf(item.quantity()), // ARGV[1]
                        String.valueOf(item.updateVersion()) // ARGV[2]
                );

                if (Long.valueOf(1L).equals(result)) {
                    log.debug("Redis 재고 등록 또는 갱신 성공. key={}", key);
                } else if (Long.valueOf(0L).equals(result)) {
                    log.debug("Redis 재고 갱신 무시. 동일하거나 과거 버전. key={}, incomingVersion={}", key, item.updateVersion());
                } else {
                    // 실패 이력 DB 저장
                    redisFailureHistoryService.recordFailure(item, "", "반환타입을 예상하지 못했음");
                }
            } catch (DataAccessException e) {
                log.error("Redis 재고 초기화 실패. item={}", item, e);
                // 실패 이력 DB 저장
                redisFailureHistoryService.recordFailure(item, "", e.getMessage());
            }
        }
    }
}
