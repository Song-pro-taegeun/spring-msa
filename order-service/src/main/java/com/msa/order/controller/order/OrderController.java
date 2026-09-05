package com.msa.order.controller.order;

import com.msa.order.dto.OrderAcceptedResponse;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/order")
public class OrderController {
    private final OrderService orderService;

    /**
     * 테넌트 provision 후 필터 및 인증인가, 스키마 동적 변경 가능한지 체크 용도의 api
     */
    @GetMapping("/me")
    public ResponseEntity<String> getMe(){
        return ResponseEntity.ok(orderService.getMe());
    }

    /**
     * 1. 레디스 재고 선점 및 재고 차감
     * 2. Order Service에 주문내역 추가
     */
    @PostMapping("/purchaseProduct")
    public ResponseEntity<Void> purchaseProduct(
            @RequestBody OrderRequestPurchaseDto orderRequestPurchaseDto
    ){
        orderService.purchaseProduct(orderRequestPurchaseDto);
        return ResponseEntity.ok().build();
    }

    /**
     * Redis Lua + Redis Stream
     * 1. 레디스 재고 선점 및 재고 차감
     * 1-2. 레디스 재고 예약(stream)
     * 2. 레디스 stream 컨슈머를 통한 db 적재 처리
     */
    @PostMapping("/purchaseProduct/redisOnly")
    public ResponseEntity<OrderAcceptedResponse> redisOnlyPurchaseProduct(
            @RequestBody OrderRequestPurchaseDto orderRequestPurchaseDto
    ){
        return ResponseEntity.accepted().body(orderService.redisOnlyPurchaseProduct(orderRequestPurchaseDto));
    }
}
