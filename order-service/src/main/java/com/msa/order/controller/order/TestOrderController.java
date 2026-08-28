package com.msa.order.controller.order;

import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.service.order.TestOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/test/order")
@RestController
public class TestOrderController {
    private final TestOrderService testOrderService;

    /**
     * 비관적락 재고 감소
     */
    public ResponseEntity<Boolean> purchaseProductPessimisticLock(
            @RequestBody OrderRequestPurchaseDto orderRequestPurchaseDto
    ){
        return ResponseEntity.ok(testOrderService.decreaseWithPessimisticLock(orderRequestPurchaseDto));
    }

    /**
     * 조건부 업데이트 재고 감소
     */
    public ResponseEntity<Boolean> purchaseProductConditionalUpdate(
            @RequestBody OrderRequestPurchaseDto orderRequestPurchaseDto
    ){
        return ResponseEntity.ok(testOrderService.decreaseConditionally(orderRequestPurchaseDto));
    }

}
