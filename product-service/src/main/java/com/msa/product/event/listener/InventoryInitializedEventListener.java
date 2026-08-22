package com.msa.product.event.listener;

import com.msa.product.event.internal.InventoryInitializedEvent;
import com.msa.product.service.redis.InventoryRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class InventoryInitializedEventListener {
    private final InventoryRedisService inventoryRedisService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false // 트랜잭션이 없으면 실행하지 않는다.
    )
    public void handle(InventoryInitializedEvent event) {
        inventoryRedisService.initializeInventories(event.items());
    }
}