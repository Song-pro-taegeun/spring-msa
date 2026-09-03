package com.msa.order.service.order;

import com.msa.order.domain.redis.InventoryReserveResult;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.master.order.OrderProductSnapshot;
import com.msa.order.entity.tenant.order.Orders;
import com.msa.order.entity.tenant.order.Users;
import com.msa.order.repository.master.order.OrderProductSnapshotRepository;
import com.msa.order.repository.master.order.TestProductSnapshotRepository;
import com.msa.order.repository.tenant.order.OrdersRepository;
import com.msa.order.repository.tenant.order.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Service
public class OrderCommandService {
    private final OrdersRepository ordersRepository;
    private final UsersRepository usersRepository;
    private final OrderProductSnapshotRepository orderProductSnapshotRepository;
    private final TestProductSnapshotRepository testRepository;

    // 주문 생성 (테넌트 db - 기본 값)
    @Transactional
    public void createProductOrder(InventoryReserveResult reserveResult, OrderRequestPurchaseDto orderRequestPurchaseDto){
        // 컨텍스트에서 유저 정보 가져오기
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        Users user = usersRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("유저를 찾을 수 없습니다: " + userId));

        // Redis 상품 버전(request 버전과 레디스 버전 확인)
        if (!orderRequestPurchaseDto.getRequestUpdateVersion().equals(reserveResult.updateVersion())) {
            throw new IllegalStateException("Order service:재고선점 - Redis 재고 버전과 요청 버전이 일치하지 않습니다.");
        }

        Orders order = Orders.create(user); // 주문 생성
        order.addItem(
                reserveResult.productOptionId(),
                orderRequestPurchaseDto.getQuantity(),
                reserveResult.price(),
                reserveResult.currency(),
                reserveResult.updateVersion()
        ); // 주문 아이템 생성

        ordersRepository.save(order);
    }

    @Transactional(
            transactionManager = "masterTransactionManager",
            readOnly = true
    )
    public OrderProductSnapshot findProductOption(OrderRequestPurchaseDto orderRequestPurchaseDto) {
        OrderProductSnapshot snapshot = orderProductSnapshotRepository.findById(orderRequestPurchaseDto.getProductOptionId())
                .orElseThrow(() -> new ResourceNotFoundException("상품 스냅샷이 없습니다: " + orderRequestPurchaseDto.getProductOptionId()));

        // 요청 당시 클라이언트가 확인했던 버전(request 버전과 스냅샷 버전 확인)
        if (!orderRequestPurchaseDto.getRequestUpdateVersion().equals(snapshot.getUpdateVersion())) {
            throw new IllegalStateException("Order service:재고선점 - 스냅샷 재고 버전과 요청 버전이 일치하지 않습니다.");
        }

        return snapshot;
    }


    /**
     * 임시 - 부하테스트용
     */
    @Transactional
    public void createOrder(InventoryReserveResult reserveResult, OrderRequestPurchaseDto orderRequestPurchaseDto){
        // 컨텍스트에서 유저 정보 가져오기
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        Users user = usersRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("유저를 찾을 수 없습니다: " + userId));

        // Redis 상품 버전(request 버전과 레디스 버전 확인)
        if (!orderRequestPurchaseDto.getRequestUpdateVersion().equals(reserveResult.updateVersion())) {
            throw new IllegalStateException("Order service:재고선점 - Redis 재고 버전과 요청 버전이 일치하지 않습니다.");
        }

        Orders order = Orders.create(user); // 주문 생성
        order.addItem(
                orderRequestPurchaseDto.getProductOptionId(),
                orderRequestPurchaseDto.getQuantity(),
                BigDecimal.valueOf(1200),
                "KRW",
                reserveResult.updateVersion()
        ); // 주문 아이템 생성

        ordersRepository.save(order);
    }

    /**
     * 임시 - 부하테스트용
     */
    @Transactional(transactionManager = "masterTransactionManager")
    public int decreaseStockConditionally(Long productOptionId, int quantity){
        return testRepository.decreaseStockConditionally(productOptionId, quantity);
    }
}
