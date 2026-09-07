package com.msa.order.service.order;

import com.msa.order.entity.tenant.order.Orders;
import com.msa.order.entity.tenant.order.Users;
import com.msa.order.redis.stream.OrderAcceptedStreamEvent;
import com.msa.order.repository.tenant.order.OrdersRepository;
import com.msa.order.repository.tenant.order.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * redis stream 처리 commandService
 */
@Service
@RequiredArgsConstructor
public class OrderStreamCommandService {
    private final OrdersRepository ordersRepository;
    private final UsersRepository usersRepository;

    @Transactional
    public boolean createOrder(OrderAcceptedStreamEvent event) {
        // DB 커밋 후 ACK가 실패해 같은 메시지가 다시 들어오는 경우 중복 저장을 막는다.
        if (ordersRepository.existsByEventId(event.eventId())) return false;

        Users user = usersRepository.findById(event.userId()).orElseThrow(() -> new ResourceNotFoundException("유저를 찾을 수 없습니다: " + event.userId()));

        Orders order = Orders.create(event.eventId(), user);
        order.addItem(
                event.productOptionId(),
                event.quantity(),
                event.price(),
                event.currency(),
                event.updateVersion()
        );

        ordersRepository.save(order);
        return true;
    }
}
