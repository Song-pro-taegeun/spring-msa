package com.msa.order.service.order;

import com.msa.order.domain.redis.InventoryReserveResult;
import com.msa.order.dto.OrderAcceptedResponse;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.tenant.order.Users;
import com.msa.order.repository.tenant.order.UsersRepository;
import com.msa.order.service.exception.OrderExceptionService;
import com.msa.tenant.context.TenantContext;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private static final String INVENTORY_KEY_PREFIX = "shared:product-service:inventory:product-option:";

    private final OrderExceptionService orderExceptionService;
    private final UsersRepository usersRepository;
    private final StringRedisTemplate redisTemplate;

    private final RedisScript<List> reserveInventoryScript;
    private final RedisScript<Long> compensateInventoryScript;
    private final RedisScript<List> acceptRedisOrderScript;

    private final OrderCommandService orderCommandService;

    public OrderService(
            OrderExceptionService orderExceptionService,
            UsersRepository usersRepository,
            StringRedisTemplate redisTemplate,

            @Qualifier("reserveInventoryScript")
            RedisScript<List> reserveInventoryScript,

            @Qualifier("compensateInventoryScript")
            RedisScript<Long> compensateInventoryScript,

            @Qualifier("acceptRedisOrderScript")
            RedisScript<List> acceptRedisOrderScript,

            OrderCommandService orderCommandService
    ){
        this.orderExceptionService = orderExceptionService;
        this.usersRepository = usersRepository;
        this.redisTemplate = redisTemplate;

        this.reserveInventoryScript = reserveInventoryScript;
        this.compensateInventoryScript = compensateInventoryScript;
        this.acceptRedisOrderScript = acceptRedisOrderScript;

        this.orderCommandService = orderCommandService;
    }

    public String getMe(){
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        return user.getUserId();
    }

    public void purchaseProduct(OrderRequestPurchaseDto orderRequestPurchaseDto){
        Long productOptionId = orderRequestPurchaseDto.getProductOptionId();
        String key = INVENTORY_KEY_PREFIX + productOptionId;
        Integer requestQuantityValue = orderRequestPurchaseDto.getQuantity();

        InventoryReserveResult reserveResult = reserveRedisInventory(key, orderRequestPurchaseDto);
        try {
            // 2. 주문 / 주문 아이템 생성
            orderCommandService.createProductOrder(reserveResult, orderRequestPurchaseDto);
        } catch (RuntimeException e) {
            try{
                // redis 재고 보상 수행
                compensateInventory(
                        key,
                        orderRequestPurchaseDto.getQuantity(), // 요청 수량 = 복구 수량
                        reserveResult.updateVersion() // 선점 당시 업데이트 버전
                );
            } catch (RuntimeException ex) {
                // 시스템 익셉션 로깅
                orderExceptionService.recordException("OrderService.compensateInventory", ex, orderRequestPurchaseDto);
                throw ex;
            }
            throw e;
        }
    }

    private void compensateInventory(String key, Integer quantity, long updateVersion){
        Long result = redisTemplate.execute(
                compensateInventoryScript,
                List.of(key),
                String.valueOf(quantity),
                String.valueOf(updateVersion)
        );

       if(result == -1){
           throw new IllegalStateException("Order service:재고 보상 트랜잭션 - 버전 불일치로 인한 업데이트 보상 트랜잭션 실패 key:" + key
                   + ", quantity: " + quantity + ", updateVersion: " + updateVersion
           );
       }
    }

    // redis 재고 선점
    private InventoryReserveResult reserveRedisInventory(String key, OrderRequestPurchaseDto orderRequestPurchaseDto){
        // 결과 값
        // result[0] 상태값: -3(버전 불일치)/ -2(잘못된 요청) / -1(상품재고 또는 버전의 값이 없음) / 0(재고 부족) / 1(재고 선점)
        List<?> result = redisTemplate.execute(
                reserveInventoryScript,
                List.of(key),
                String.valueOf(orderRequestPurchaseDto.getQuantity()),
                String.valueOf(orderRequestPurchaseDto.getRequestUpdateVersion())
        );

        // 결과가 없거나 3개의 리스트가 아닐 때,
        if (result == null || result.size() != 7) {
            throw new IllegalStateException("Order service:재고선점 - Redis 재고 처리 결과가 올바르지 않습니다");
        }

        long status = ((Number) result.get(0)).longValue();

        switch ((int) status){
            case 1:
                break;
            case 0:
                throw new IllegalStateException("Order service:재고선점 - 재고가 부족합니다.");
            case -1:
                throw new IllegalStateException("Order service:재고선점 - 상품재고 또는 버전의 값이 없습니다.");
            case -2:
                throw new IllegalStateException("Order service:재고선점 - 요청 수량 또는 버전이 잘못되었습니다.");
            case -3:
                throw new IllegalStateException("Order service:재고선점 - 요청 버전이 재고 버전과 다릅니다.");
            default:
                throw new IllegalStateException("Order service:재고선점 - 알 수 없는 Redis 처리 상태입니다: " + status);
        }

        return new InventoryReserveResult(
                true,
                toLong(result.get(1)),
                toLong(result.get(2)),
                Math.toIntExact(toLong(result.get(3))),
                toLong(result.get(4)),
                new BigDecimal(String.valueOf(result.get(5))),
                String.valueOf(result.get(6))
        );
    }

    // 레디스 전용 주문처리 서비스 로직
    public OrderAcceptedResponse redisOnlyPurchaseProduct(OrderRequestPurchaseDto request) {
        Long productOptionId = request.getProductOptionId();
        String inventoryKey = INVENTORY_KEY_PREFIX + productOptionId;

        String tenantKey = TenantContext.get();

        // redis hash 및 stream에서 사용
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // 주문 접수 이벤트 식별자(멱등성을 위한 eventId)
        String eventId = UUID.randomUUID().toString();
        long acceptedAt = System.currentTimeMillis();

        // redis + lua 주문 처리
        reserveRedisOrder(inventoryKey, request, eventId, tenantKey, userId, acceptedAt);
        return new OrderAcceptedResponse(eventId, "ACCEPTED");
    }

    private void reserveRedisOrder(
            String inventoryKey,
            OrderRequestPurchaseDto request,
            String eventId,
            String tenantKey,
            String userId,
            long acceptedAt
    ) {
        // redis 자료구조 키
        Long productOptionId = request.getProductOptionId();
        String orderKeyPrefix = "order:product-option:" + productOptionId;

        String eventKey = orderKeyPrefix + ":events";
        String ledgerKey = orderKeyPrefix + ":ledger";
        String streamKey = orderKeyPrefix + ":stream";

        // redis 재고감소 + 주문 + 원장 + 에빈트 멱등성 기록 등 원자 스크립트 실행
        List<?> result = redisTemplate.execute(
                acceptRedisOrderScript,
                List.of(
                        inventoryKey,
                        eventKey,
                        ledgerKey,
                        streamKey
                ),
                String.valueOf(request.getQuantity()),
                String.valueOf(request.getRequestUpdateVersion()),
                eventId,
                tenantKey,
                userId,
                String.valueOf(acceptedAt)
        );

        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Order service - redis only: Redis 주문 접수 결과가 없습니다.");
        }

        long status = toLong(result.get(0));
        switch ((int) status) {
            case 1: // 신규 주문 접수 성공
                return;
            case 2: // 동일 eventId가 이미 처리됨
                return;
            case 0:
                throw new IllegalStateException("Order service - redis only: 재고가 부족합니다.");
            case -1:
                throw new IllegalStateException("Order service - redis only: Redis 상품 정보가 없거나 정상적으로 등록되지 않은 상품입니다.");
            case -2:
                throw new IllegalStateException("Order service - redis only: 주문 요청값이 잘못되었습니다.");
            case -3:
                throw new IllegalStateException("Order service - redis only: 상품 버전이 일치하지 않습니다.");
            case -4:
                throw new IllegalStateException("Order service - redis only: Redis 데이터 형식이 잘못되었습니다.");
            default:
                throw new IllegalStateException("Order service - redis only: 알 수 없는 Redis 처리 상태입니다: " + status);
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String string) {
            return Long.parseLong(string);
        }

        throw new IllegalStateException(
                "Redis 값을 Long으로 변환할 수 없습니다. value="
                        + value
                        + ", type="
                        + (value == null ? "null" : value.getClass().getName())
        );
    }
}
