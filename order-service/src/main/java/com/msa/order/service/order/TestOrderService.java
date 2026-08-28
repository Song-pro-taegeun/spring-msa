package com.msa.order.service.order;

import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.master.order.TestProductSnapshot;
import com.msa.order.repository.master.order.TestProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TestOrderService {
    private final TestProductSnapshotRepository repository;

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
            return false;
        }

        snapshot.decrease(quantity);
        // 트랜잭션 종료 시 dirty checking으로 UPDATE 실행
        return true;
    }

    @Transactional(transactionManager = "masterTransactionManager")
    public boolean decreaseConditionally(OrderRequestPurchaseDto orderRequestPurchaseDto) {
        Long productOptionId = orderRequestPurchaseDto.getProductOptionId();
        int quantity = orderRequestPurchaseDto.getQuantity();

        return repository.decreaseStockConditionally(
                productOptionId,
                quantity
        ) == 1;
    }
}
