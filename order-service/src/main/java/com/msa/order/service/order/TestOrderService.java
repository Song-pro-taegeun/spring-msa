package com.msa.order.service.order;

import com.msa.order.domain.redis.InventoryReserveResult;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.master.order.TestProductSnapshot;
import com.msa.order.repository.master.order.TestProductSnapshotRepository;
import com.msa.order.service.exception.OrderExceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TestOrderService {
    private final TestProductSnapshotRepository repository;
    private final OrderCommandService orderCommandService;
    private final OrderExceptionService orderExceptionService;

    @Transactional(transactionManager = "masterTransactionManager")
    public boolean decreaseWithPessimisticLock(OrderRequestPurchaseDto orderRequestPurchaseDto) {
        Long productOptionId = orderRequestPurchaseDto.getProductOptionId();
        int quantity = orderRequestPurchaseDto.getQuantity();

        TestProductSnapshot snapshot = repository
                .findByIdForUpdate(productOptionId)
                .orElseThrow(() ->
                        new IllegalArgumentException("상품 옵션이 없습니다: " + productOptionId)
                );

        if (!snapshot.hasEnoughStock(quantity)) {
            throw new IllegalStateException("재고 없음.");
        }

        snapshot.decrease(quantity);
        InventoryReserveResult reserveResult = new InventoryReserveResult(true, quantity, orderRequestPurchaseDto.getRequestUpdateVersion());
        try {
            // 2. 주문 / 주문 아이템 생성
            orderCommandService.createOrder(reserveResult, orderRequestPurchaseDto);
            return true;
        } catch (RuntimeException e) {
            orderExceptionService.recordException("TestOrderService.decreaseWithPessimisticLock", e, orderRequestPurchaseDto);
            throw e;
        }
        // 트랜잭션 종료 시 dirty checking으로 UPDATE 실행
    }

    public boolean decreaseConditionally(OrderRequestPurchaseDto orderRequestPurchaseDto) {
        Long productOptionId = orderRequestPurchaseDto.getProductOptionId();
        int quantity = orderRequestPurchaseDto.getQuantity();
        int result = orderCommandService.decreaseStockConditionally(productOptionId, quantity);

        if(result > 0){
            InventoryReserveResult reserveResult = new InventoryReserveResult(true, quantity, orderRequestPurchaseDto.getRequestUpdateVersion());
            try {
                // 2. 주문 / 주문 아이템 생성
                orderCommandService.createOrder(reserveResult, orderRequestPurchaseDto);
            } catch (RuntimeException e) {
                orderExceptionService.recordException("TestOrderService.decreaseConditionally", e, orderRequestPurchaseDto);
                throw e;
            }

            return true;
        } else {
            return false;
        }
    }
}
