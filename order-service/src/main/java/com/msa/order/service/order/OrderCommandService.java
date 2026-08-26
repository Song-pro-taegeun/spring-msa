package com.msa.order.service.order;

import com.msa.order.domain.redis.InventoryReserveResult;
import com.msa.order.dto.OrderRequestPurchaseDto;
import com.msa.order.entity.order.Orders;
import com.msa.order.entity.order.Users;
import com.msa.order.repository.order.OrdersRepository;
import com.msa.order.repository.order.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderCommandService {
    private final OrdersRepository ordersRepository;
    private final UsersRepository usersRepository;

    // 주문 생성
    @Transactional
    public void createOrder(Long productOptionId, InventoryReserveResult reserveResult, OrderRequestPurchaseDto orderRequestPurchaseDto){
        // 컨텍스트에서 유저 정보 가져오기
        String userId = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        Users user = usersRepository.findById(userId).orElseThrow(() ->
                new ResourceNotFoundException("유저를 찾을 수 없습니다: " + userId));

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
