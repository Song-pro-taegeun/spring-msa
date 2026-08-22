package com.msa.product.service.redis;

import com.msa.product.event.internal.InventoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisFailureHistoryService {

    // TODO: DDL, Entity 생성 후 작업 필요~~~~~
    // 이벤트 익셉션(아웃박스, DLQ)를 제외 한 프로세스 익셉션을 하나로 둘 건지, redis 전용으로 둘 건지 고민 필요
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            InventoryItem item,
            String failureType,
            String errorMessage
    ) {
    // 실패 이력 저장
    }
}
