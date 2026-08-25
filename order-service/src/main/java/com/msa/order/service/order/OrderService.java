package com.msa.order.service.order;

import com.msa.order.domain.redis.InventoryReserveResult;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.order.Orders;
import com.msa.order.entity.order.Users;
import com.msa.order.repository.order.OrdersRepository;
import com.msa.order.repository.order.UsersRepository;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {
    private static final String INVENTORY_KEY_PREFIX = "shared:product-service:inventory:product-option:";

    private final UsersRepository usersRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> reserveInventoryScript;
    private final OrdersRepository ordersRepository;

    public OrderService(
            UsersRepository usersRepository,
            StringRedisTemplate redisTemplate,

            @Qualifier("reserveInventoryScript")
            RedisScript<List> reserveInventoryScript,
            OrdersRepository ordersRepository
    ){
        this.usersRepository = usersRepository;
        this.redisTemplate = redisTemplate;
        this.reserveInventoryScript = reserveInventoryScript;
        this.ordersRepository = ordersRepository;
    }

    public String getMe(){
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        return user.getUserId();
    }

    @Transactional
    public void purchaseProduct(OrderRequestPurchaseDto orderRequestPurchaseDto){
        Long productOptionId = orderRequestPurchaseDto.getProductOptionId();
        String key = INVENTORY_KEY_PREFIX + productOptionId;
        Integer requestQuantityValue = orderRequestPurchaseDto.getQuantity();

        // 1. redis rua 실행
        InventoryReserveResult reserveResult = reserveRedisInventory(key, requestQuantityValue);

        // 2. 주문 / 주문 아이템 생성
        try {
            createOrder(productOptionId, reserveResult, orderRequestPurchaseDto);
        } catch (RuntimeException e) {
            // TODO: 저장 실패 시 보상 트랜잭션 발행 필요@@@@@

            throw e;
        }
    }

    // redis 재고 선점
    private InventoryReserveResult reserveRedisInventory(String key, Integer requestQuantityValue){
        // 결과 값
        // result[0] 상태값: -2(잘못된 요청) / -1(상품재고 또는 버전의 값이 없음) / 0(재고 부족) / 1(재고 선점)
        List<?> result = redisTemplate.execute(
                reserveInventoryScript,
                List.of(key),
                String.valueOf(requestQuantityValue)
        );

        // 결과가 없거나 3개의 리스트가 아닐 때,
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Order service:재고선점 - Redis 재고 처리 결과가 올바르지 않습니다");
        }

        long status = ((Number) result.get(0)).longValue();
        Integer remainingQuantity = Math.toIntExact(((Number) result.get(1)).longValue());
        long updateVersion = ((Number) result.get(2)).longValue();

        switch ((int) status){
            case 1:
                break;
            case 0:
                throw new IllegalStateException("Order service:재고선점 - 재고가 부족합니다.");
            case -1:
                throw new IllegalStateException("Order service:재고선점 - 상품재고 또는 버전의 값이 없습니다.");
            case -2:
                throw new IllegalStateException("Order service:재고선점 - 요청 수량이 잘못되었습니다.");
            default:
                throw new IllegalStateException("Order service:재고선점 - 알 수 없는 Redis 처리 상태입니다: " + status);
        }

        return new InventoryReserveResult(
                status == 1,
                        remainingQuantity,
                        updateVersion
                );
    }

    // 주문 생성
    private void createOrder(Long productOptionId, InventoryReserveResult reserveResult, OrderRequestPurchaseDto orderRequestPurchaseDto){
        // a. 컨텍스트에서 유저 정보 가져오기
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        Users user = usersRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("유저를 찾을 수 없습니다: " + userId));

        // b. 스냅샷에서 제품 정보 가져오기
        if (!orderRequestPurchaseDto.getRequestUpdateVersion().equals(reserveResult.updateVersion())) {
            throw new IllegalStateException("Order service:재고선점 - Redis 재고 버전과 요청 버전이 일치하지 않습니다.");
        }

        Orders order = Orders.create(user); // 주문 생성
        order.addItem(
                productOptionId,
                orderRequestPurchaseDto.getQuantity(),
                orderRequestPurchaseDto.getUnitPrice(),
                orderRequestPurchaseDto.getCurrency(),
                reserveResult.updateVersion()
        ); // 주문 아이템 생성

        ordersRepository.save(order);
    }
}
