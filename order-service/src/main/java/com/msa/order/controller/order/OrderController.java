package com.msa.order.controller.order;

import com.msa.order.service.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
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
     * 3. 아웃박스 테이블에 데이터 적재
     *
     * 일단 테스트 용도로 파라메터 없이 만들고
     * product Service DDL이 완성되면 @RequestBody 데이터 추가
     */
    @PostMapping("/purchaseProduct")
    public void purchaseProduct(){
        orderService.purchaseProduct();
    }
}
